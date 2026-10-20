package com.tickethub.booking.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Контракти, через які модулі payment і ticket працюють із замовленнями, не торкаючись їхніх сутностей. */
public final class OrderPayments {

    private OrderPayments() {
    }

    public record PayableOrder(UUID orderId, BigDecimal amount, String currency) {
    }

    public enum PaymentConfirmation {
        /** Замовлення оплачено, місця продано. */
        PAID,
        /** Гроші треба повернути: місця вже неможливо продати цьому замовленню. */
        REFUND_REQUIRED
    }

    public record PaidOrder(UUID orderId, UUID userId, UUID eventId, List<PaidItem> items) {
    }

    public record PaidItem(UUID orderItemId, UUID eventSeatId, BigDecimal price) {
    }
}
