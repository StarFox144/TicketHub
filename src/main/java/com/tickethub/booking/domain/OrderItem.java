package com.tickethub.booking.domain;

import com.tickethub.common.audit.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Позиція замовлення. active = false після скасування/протухання: частковий унікальний індекс
 * uq_order_items_active_seat гарантує на рівні БД, що місце не потрапить у два активні замовлення.
 */
@Entity
@Table(name = "order_items")
public class OrderItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Order order;

    /** Посилання на інший модуль — лише за id, без JPA-зв'язку. */
    @Column(nullable = false, updatable = false)
    private UUID eventSeatId;

    /** Ціна, зафіксована на момент покупки: подальша зміна цін зони її не зачіпає. */
    @Column(nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private boolean active = true;

    protected OrderItem() {
    }

    OrderItem(Order order, UUID eventSeatId, BigDecimal price) {
        this.order = order;
        this.eventSeatId = eventSeatId;
        this.price = price;
    }

    void deactivate() {
        active = false;
    }

    public UUID getEventSeatId() {
        return eventSeatId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public boolean isActive() {
        return active;
    }
}
