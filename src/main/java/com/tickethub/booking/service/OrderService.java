package com.tickethub.booking.service;

import com.tickethub.booking.api.dto.CreateOrderRequest;
import com.tickethub.booking.api.dto.OrderView;
import com.tickethub.booking.domain.Order;
import com.tickethub.booking.repository.OrderRepository;
import com.tickethub.common.exception.BusinessRuleException;
import com.tickethub.common.exception.NotFoundException;
import com.tickethub.common.security.CurrentUser;
import com.tickethub.event.domain.SeatsUnavailableException;
import com.tickethub.event.service.EventSeatService;
import com.tickethub.user.domain.Role;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final EventSeatService eventSeatService;
    private final BookingProperties properties;
    private final OrderMapper mapper;
    private final Clock clock;
    private final TransactionTemplate tx;

    public OrderService(OrderRepository orderRepository, EventSeatService eventSeatService,
                        BookingProperties properties, OrderMapper mapper, Clock clock,
                        PlatformTransactionManager transactionManager) {
        this.orderRepository = orderRepository;
        this.eventSeatService = eventSeatService;
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
        try {
            return new PlacedOrder(tx.execute(status -> reserve(userId, request, idempotencyKey)), true);
        } catch (SeatsUnavailableException | DataIntegrityViolationException e) {
            return findReplay(userId, request, idempotencyKey)
                    .map(order -> new PlacedOrder(order, false))
                    .orElseThrow(() -> e);
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

    /** Скасувати можна лише PENDING; оплачене замовлення — через повернення коштів (етап 5). */
    @Transactional
    public OrderView cancel(UUID orderId, UUID userId) {
        var order = orderRepository.findForUpdateById(orderId)
                .filter(o -> o.isOwnedBy(userId))
                .orElseThrow(() -> new NotFoundException("Order", orderId));
        order.cancel();
        eventSeatService.release(order.seatIds());
        return mapper.toView(order, clock.instant());
    }
}
