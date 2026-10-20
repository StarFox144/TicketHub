package com.tickethub.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tickethub.booking.api.dto.CreateOrderRequest;
import com.tickethub.booking.service.OrderService;
import com.tickethub.payment.api.dto.PaymentWebhook;
import com.tickethub.payment.service.PaymentService;
import com.tickethub.payment.service.WebhookSignature;
import com.tickethub.user.domain.User;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Сценарій оплати без HTTP-виклику провайдера: тест сам формує підписаний webhook, як це зробив би провайдер. */
@Component
public class PaymentTestSupport {

    public record StartedPayment(UUID orderId, String providerPaymentId, BigDecimal amount) {
    }

    private final OrderService orderService;
    private final PaymentService paymentService;
    private final WebhookSignature signature;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public PaymentTestSupport(OrderService orderService, PaymentService paymentService, WebhookSignature signature,
                              ObjectMapper objectMapper, Clock clock) {
        this.orderService = orderService;
        this.paymentService = paymentService;
        this.signature = signature;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public UUID placeOrder(User buyer, UUID eventId, UUID... seatIds) {
        return orderService.placeOrder(buyer.getId(), new CreateOrderRequest(eventId, List.of(seatIds)),
                UUID.randomUUID().toString()).order().orderId();
    }

    public StartedPayment startPayment(User buyer, UUID orderId) {
        var payment = paymentService.startPayment(orderId, buyer.getId());
        var path = payment.checkoutUrl().getPath();
        return new StartedPayment(orderId, path.substring(path.lastIndexOf('/') + 1), payment.amount());
    }

    public MockHttpServletRequestBuilder webhook(StartedPayment payment, String status) throws Exception {
        return webhook(payment.providerPaymentId(), status, payment.amount(), clock.instant());
    }

    public MockHttpServletRequestBuilder webhook(String providerPaymentId, String status, BigDecimal amount,
                                                Instant signedAt) throws Exception {
        var body = objectMapper.writeValueAsString(
                new PaymentWebhook(providerPaymentId, status, amount, "UAH", signedAt));
        long timestamp = signedAt.getEpochSecond();
        return post("/api/v1/payments/webhook")
                .contentType("application/json")
                .header(WebhookSignature.TIMESTAMP_HEADER, String.valueOf(timestamp))
                .header(WebhookSignature.SIGNATURE_HEADER, signature.sign(timestamp, body))
                .content(body);
    }
}
