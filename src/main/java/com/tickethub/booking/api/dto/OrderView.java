package com.tickethub.booking.api.dto;

import com.tickethub.booking.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** secondsUntilExpiry — скільки лишилося до кінця резерву (0 для не-PENDING замовлень). */
public record OrderView(
        UUID orderId,
        UUID eventId,
        OrderStatus status,
        BigDecimal totalAmount,
        String currency,
        Instant expiresAt,
        long secondsUntilExpiry,
        List<Item> items,
        Instant createdAt
) {

    public record Item(UUID eventSeatId, BigDecimal price) {
    }
}
