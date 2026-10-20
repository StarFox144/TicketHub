package com.tickethub.booking.service;

import com.tickethub.booking.api.dto.CreateOrderRequest;
import com.tickethub.booking.api.dto.OrderView;
import com.tickethub.booking.domain.Order;
import com.tickethub.booking.domain.OrderItem;
import com.tickethub.booking.domain.OrderStatus;
import com.tickethub.booking.messaging.OrderPaid;
import com.tickethub.booking.repository.OrderRepository;
import com.tickethub.booking.service.OrderPayments.PaidItem;
import com.tickethub.booking.service.OrderPayments.PaidOrder;
import com.tickethub.booking.service.OrderPayments.PayableOrder;
import com.tickethub.booking.service.OrderPayments.PaymentConfirmation;
import com.tickethub.booking.service.SeatClaimService.ClaimResult;
import com.tickethub.common.exception.BusinessRuleException;
import com.tickethub.common.exception.ConflictException;
import com.tickethub.common.exception.NotFoundException;
import com.tickethub.common.outbox.OutboxPublisher;
import com.tickethub.common.security.CurrentUser;
import com.tickethub.event.domain.SeatsUnavailableException;
import com.tickethub.event.service.EventSeatService;
import com.tickethub.user.domain.Role;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final EventSeatService eventSeatService;
    private final SeatClaimService seatClaims;
    private final OutboxPublisher outbox;
    private final BookingProperties properties;
    private final OrderMapper mapper;
    private final Clock clock;
    private final TransactionTemplate tx;

    public OrderService(OrderRepository orderRepository, EventSeatService eventSeatService,
                        SeatClaimService seatClaims, OutboxPublisher outbox, BookingProperties properties,
                        OrderMapper mapper, Clock clock, PlatformTransactionManager transactionManager) {
        this.orderRepository = orderRepository;
        this.eventSeatService = eventSeatService;
        this.seatClaims = seatClaims;
        this.outbox = outbox;
        this.properties = properties;
        this.mapper = mapper;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public record PlacedOrder(OrderView order, boolean created) {
    }

    /**
     * Резервує місця і створює PENDING-замовлення. Ідемпотентно за Idempotency-Key:
     * повтор того самого запиту повертає вже створене замовлення замість 409.
     * <p>
     * Транзакція керується вручну, бо після невдачі потрібна ще одна перевірка ключа в новій транзакції:
     * два паралельні запити з одним ключем обидва не бачать замовлення, переможець створює його,
     * а програвший падає на зайнятих місцях або на UNIQUE (idempotency_key) — і має отримати те саме замовлення.
     */
    public PlacedOrder placeOrder(UUID userId, CreateOrderRequest request, String idempotencyKey) {
        if (request.eventSeatIds().stream().distinct().count() > properties.maxSeatsPerOrder()) {
            throw new BusinessRuleException("At most %d seats per order".formatted(properties.maxSeatsPerOrder()));
        }
        var replay = findReplay(userId, request, idempotencyKey);
        if (replay.isPresent()) {
            return new PlacedOrder(replay.get(), false);
        }

        var claimToken = SeatClaimService.tokenFor(userId, idempotencyKey);
        List<UUID> newlyClaimed = switch (seatClaims.claim(request.eventSeatIds(), claimToken,
                properties.reservationTtl())) {
            // хтось уже тримає місце в Redis — 409 без жодного звернення до Postgres
            case ClaimResult.Taken taken -> throw new SeatsUnavailableException(taken.seatIds());
            case ClaimResult.Claimed claimed -> claimed.newlyClaimed();
            case ClaimResult.Skipped skipped -> List.of();
        };

        try {
            return new PlacedOrder(tx.execute(status -> reserve(userId, request, idempotencyKey)), true);
        } catch (RuntimeException e) {
            // БД відмовила (місце зайняте, ключ уже використано...) — звільняємо те, що щойно застовпили в Redis
            seatClaims.release(newlyClaimed, claimToken);
            if (e instanceof SeatsUnavailableException || e instanceof DataIntegrityViolationException) {
                return findReplay(userId, request, idempotencyKey)
                        .map(order -> new PlacedOrder(order, false))
                        .orElseThrow(() -> e);
            }
            throw e;
        }
    }

    private OrderView reserve(UUID userId, CreateOrderRequest request, String idempotencyKey) {
        var now = clock.instant();
        var ttl = properties.reservationTtl();
        var lines = eventSeatService.reserve(request.eventId(), request.eventSeatIds(), now, ttl).stream()
                .map(seat -> new Order.Line(seat.eventSeatId(), seat.price(), seat.currency()))
                .toList();
        var order = Order.create(userId, request.eventId(), lines, idempotencyKey, now.plus(ttl));
        // flush тут: порушення UNIQUE (idempotency_key) чи індексу активних місць має вилетіти
        // як DataIntegrityViolationException усередині try, а не під час коміту
        orderRepository.saveAndFlush(order);
        return mapper.toView(order, now);
    }

    private Optional<OrderView> findReplay(UUID userId, CreateOrderRequest request, String idempotencyKey) {
        return tx.execute(status -> orderRepository.findByIdempotencyKey(idempotencyKey).map(order -> {
            if (!order.isOwnedBy(userId) || !order.matchesRequest(request.eventId(), request.eventSeatIds())) {
                throw new BusinessRuleException("Idempotency-Key has already been used for a different request");
            }
            return mapper.toView(order, clock.instant());
        }));
    }

    @Transactional(readOnly = true)
    public OrderView get(UUID orderId, CurrentUser viewer) {
        var order = orderRepository.findById(orderId)
                .filter(o -> o.isOwnedBy(viewer.id()) || viewer.hasRole(Role.ADMIN.name()))
                .orElseThrow(() -> new NotFoundException("Order", orderId));
        return mapper.toView(order, clock.instant());
    }

    @Transactional(readOnly = true)
    public Page<OrderView> listForUser(UUID userId, Pageable pageable) {
        var now = clock.instant();
        return orderRepository.findByUserId(userId, pageable).map(order -> mapper.toView(order, now));
    }

    /** Для модуля payment: замовлення, яке цей користувач може оплатити просто зараз. */
    @Transactional(readOnly = true)
    public PayableOrder getPayableOrder(UUID orderId, UUID userId) {
        var order = orderRepository.findById(orderId)
                .filter(o -> o.isOwnedBy(userId))
                .orElseThrow(() -> new NotFoundException("Order", orderId));
        if (!order.isAwaitingPayment(clock.instant())) {
            throw new ConflictException("Order is not awaiting payment, its status is " + order.getStatus());
        }
        return new PayableOrder(order.getId(), order.getTotalAmount(), order.getCurrency());
    }

    /**
     * Для модуля payment: гроші надійшли — оформити продаж у транзакції webhook-а.
     * Замовлення блокується, тож оплата не перетнеться з ExpiredReservationJob чи скасуванням.
     * <ul>
     *   <li>PENDING — місця RESERVED → SOLD, замовлення PAID, подія order.paid в outbox;</li>
     *   <li>EXPIRED — якщо місця досі вільні, продаємо їх цьому замовленню; інакше refund;</li>
     *   <li>CANCELLED — покупець передумав, а оплата вже пройшла: refund.</li>
     * </ul>
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public PaymentConfirmation confirmPayment(UUID orderId) {
        var order = orderRepository.findForUpdateById(orderId)
                .orElseThrow(() -> new NotFoundException("Order", orderId));
        switch (order.getStatus()) {
            case PENDING -> {
                eventSeatService.markSold(order.seatIds());
                order.markPaid();
            }
            case EXPIRED -> {
                if (!eventSeatService.sellIfStillAvailable(order.seatIds())) {
                    order.markRefunded();
                    return PaymentConfirmation.REFUND_REQUIRED;
                }
                order.markPaidAfterExpiry();
            }
            case CANCELLED -> {
                order.markRefunded();
                return PaymentConfirmation.REFUND_REQUIRED;
            }
            case PAID, REFUNDED -> throw new ConflictException(
                    "Order %s has already been settled with status %s".formatted(orderId, order.getStatus()));
        }
        outbox.publish("Order", order.getId(), OrderPaid.EVENT_TYPE,
                new OrderPaid(order.getId(), order.getUserId(), order.getEventId()));
        // місця продано — ключі в Redis більше не потрібні, далі їх захищає статус SOLD у БД
        seatClaims.release(order.seatIds(), SeatClaimService.tokenFor(order.getUserId(), order.getIdempotencyKey()));
        return PaymentConfirmation.PAID;
    }

    /** Для модуля ticket: оплачене замовлення з позиціями; порожньо, якщо замовлення не (вже) оплачене. */
    @Transactional(readOnly = true)
    public Optional<PaidOrder> findPaidOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .filter(order -> order.getStatus() == OrderStatus.PAID)
                .map(order -> new PaidOrder(order.getId(), order.getUserId(), order.getEventId(),
                        order.getItems().stream()
                                .filter(OrderItem::isActive)
                                .map(item -> new PaidItem(item.getId(), item.getEventSeatId(), item.getPrice()))
                                .toList()));
    }

    /** Скасувати можна лише PENDING; повернення коштів за оплачене замовлення — поза межами MVP. */
    @Transactional
    public OrderView cancel(UUID orderId, UUID userId) {
        var order = orderRepository.findForUpdateById(orderId)
                .filter(o -> o.isOwnedBy(userId))
                .orElseThrow(() -> new NotFoundException("Order", orderId));
        order.cancel();
        eventSeatService.release(order.seatIds());
        seatClaims.release(order.seatIds(), SeatClaimService.tokenFor(userId, order.getIdempotencyKey()));
        return mapper.toView(order, clock.instant());
    }
}
