package com.tickethub.booking.messaging;

import java.util.UUID;

/**
 * Публічний контракт події «замовлення оплачено» (routing key {@value #EVENT_TYPE}).
 * Лише ідентифікатори: споживач сам читає актуальні дані, тож повідомлення не застаріває
 * і не тягне за собою персональних даних у брокер.
 */
public record OrderPaid(UUID orderId, UUID userId, UUID eventId) {

    public static final String EVENT_TYPE = "order.paid";
}
