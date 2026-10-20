package com.tickethub.payment.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Тіло webhook-а від платіжного провайдера. status — SUCCEEDED або FAILED. */
public record PaymentWebhook(
        String providerPaymentId,
        String status,
        BigDecimal amount,
        String currency,
        Instant occurredAt
) {

    public boolean succeeded() {
        return "SUCCEEDED".equals(status);
    }
}
