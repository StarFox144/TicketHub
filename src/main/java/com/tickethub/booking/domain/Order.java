package com.tickethub.booking.domain;

import com.tickethub.common.audit.BaseEntity;
import com.tickethub.common.exception.BusinessRuleException;
import com.tickethub.common.exception.ConflictException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Ім'я сутності не "Order": це ключове слово JPQL. */
@Entity(name = "BookingOrder")
@Table(name = "orders")
public class Order extends BaseEntity {

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false, unique = true, updatable = false, length = 64)
    private String idempotencyKey;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
    }

    public record Line(UUID eventSeatId, BigDecimal price, String currency) {
    }

    public static Order create(UUID userId, UUID eventId, List<Line> lines, String idempotencyKey, Instant expiresAt) {
        if (lines.isEmpty()) {
            throw new BusinessRuleException("Order must contain at least one seat");
        }
        var currencies = lines.stream().map(Line::currency).distinct().toList();
        if (currencies.size() > 1) {
            throw new BusinessRuleException("All seats of one order must be priced in the same currency");
        }
        var order = new Order();
        order.userId = userId;
        order.eventId = eventId;
        order.idempotencyKey = idempotencyKey;
        order.expiresAt = expiresAt;
        order.currency = currencies.getFirst();
        order.totalAmount = lines.stream().map(Line::price).reduce(BigDecimal.ZERO, BigDecimal::add);
        lines.forEach(line -> order.items.add(new OrderItem(order, line.eventSeatId(), line.price())));
        return order;
    }

    public void cancel() {
        requirePending("cancelled");
        status = OrderStatus.CANCELLED;
        items.forEach(OrderItem::deactivate);
    }

    public void expire() {
        requirePending("expired");
        status = OrderStatus.EXPIRED;
        items.forEach(OrderItem::deactivate);
    }

    public void markPaid() {
        requirePending("paid");
        status = OrderStatus.PAID;
    }

    /** Оплата прийшла після протухання, але місця досі вільні й уже продані цьому замовленню. */
    public void markPaidAfterExpiry() {
        if (status != OrderStatus.EXPIRED) {
            throw new ConflictException("Order in status %s cannot be paid after expiry".formatted(status));
        }
        status = OrderStatus.PAID;
        items.forEach(OrderItem::activate);
    }

    /** Гроші надійшли, але продати місця вже неможливо (протухло й зайняте іншим або скасоване). */
    public void markRefunded() {
        if (status != OrderStatus.EXPIRED && status != OrderStatus.CANCELLED) {
            throw new ConflictException("Order in status %s cannot be refunded".formatted(status));
        }
        status = OrderStatus.REFUNDED;
    }

    public boolean isAwaitingPayment(Instant now) {
        return status == OrderStatus.PENDING && expiresAt.isAfter(now);
    }

    /** Чи описує це замовлення той самий запит (для перевірки повторів з тим самим Idempotency-Key). */
    public boolean matchesRequest(UUID eventId, Collection<UUID> eventSeatIds) {
        return this.eventId.equals(eventId) && new HashSet<>(seatIds()).equals(new HashSet<>(eventSeatIds));
    }

    public Duration remaining(Instant now) {
        if (status != OrderStatus.PENDING || !expiresAt.isAfter(now)) {
            return Duration.ZERO;
        }
        return Duration.between(now, expiresAt);
    }

    public boolean isOwnedBy(UUID userId) {
        return this.userId.equals(userId);
    }

    public List<UUID> seatIds() {
        return items.stream().map(OrderItem::getEventSeatId).toList();
    }

    private void requirePending(String action) {
        if (status != OrderStatus.PENDING) {
            throw new ConflictException("Order in status %s cannot be %s".formatted(status, action));
        }
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
