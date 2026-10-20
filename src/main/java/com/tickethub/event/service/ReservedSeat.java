package com.tickethub.event.service;

import java.math.BigDecimal;
import java.util.UUID;

/** Результат резервування для модуля booking: ціна фіксується в OrderItem на момент покупки. */
public record ReservedSeat(UUID eventSeatId, BigDecimal price, String currency) {
}
