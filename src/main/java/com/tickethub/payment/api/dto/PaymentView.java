package com.tickethub.payment.api.dto;

import com.tickethub.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;

/** checkoutUrl — сторінка провайдера, куди фронтенд перенаправляє покупця. */
public record PaymentView(
        UUID paymentId,
        UUID orderId,
        PaymentStatus status,
        BigDecimal amount,
        String currency,
        URI checkoutUrl
) {
}
