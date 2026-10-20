package com.tickethub.payment.api;

import com.tickethub.support.IntegrationTestBase;
import com.tickethub.support.PaymentTestSupport;
import com.tickethub.support.TestData.PublishedEvent;
import com.tickethub.user.domain.Role;
import com.tickethub.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentWebhookIT extends IntegrationTestBase {

    @Autowired
    private PaymentTestSupport payments;

    @Autowired
    private JdbcTemplate jdbc;

    private PublishedEvent event;
    private User buyer;

    @BeforeEach
    void setUp() {
        event = testData.publishedEvent();
        buyer = testData.user(Role.USER);
    }

    @Test
    void pay_returnsCheckoutUrl_andRepeatedCallReusesPayment() throws Exception {
        var orderId = payments.placeOrder(buyer, event.eventId(), event.seat(0), event.seat(5));

        var first = mockMvc.perform(post("/api/v1/orders/{id}/pay", orderId)
                        .header(HttpHeaders.AUTHORIZATION, testData.bearer(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.amount").value(1500.00))
                .andReturn().getResponse().getContentAsString();
        var second = mockMvc.perform(post("/api/v1/orders/{id}/pay", orderId)
                        .header(HttpHeaders.AUTHORIZATION, testData.bearer(buyer)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(first).get("checkoutUrl").asText()).contains("/fake-provider/checkout/fake_");
        assertThat(objectMapper.readTree(second).get("paymentId")).isEqualTo(objectMapper.readTree(first).get("paymentId"));
    }

    @Test
    void pay_foreignOrder_returns404() throws Exception {
        var orderId = payments.placeOrder(buyer, event.eventId(), event.seat(0));

        mockMvc.perform(post("/api/v1/orders/{id}/pay", orderId)
                        .header(HttpHeaders.AUTHORIZATION, testData.bearer(Role.USER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void successfulPayment_sellsSeats_andWritesOutboxEventOnce() throws Exception {
        var orderId = payments.placeOrder(buyer, event.eventId(), event.seat(0), event.seat(1));
        var payment = payments.startPayment(buyer, orderId);

        mockMvc.perform(payments.webhook(payment, "SUCCEEDED")).andExpect(status().isOk());
        // провайдер повторив webhook (наприклад, не дочекався нашої відповіді) — нічого не змінюється
        mockMvc.perform(payments.webhook(payment, "SUCCEEDED")).andExpect(status().isOk());

        assertThat(orderStatus(orderId)).isEqualTo("PAID");
        assertThat(paymentStatus(orderId)).isEqualTo("SUCCEEDED");
        assertThat(seatStatus(event.seat(0))).isEqualTo("SOLD");
        assertThat(seatStatus(event.seat(1))).isEqualTo("SOLD");
        assertThat(jdbc.queryForList("select event_type from outbox where aggregate_id = ?", String.class, orderId))
                .containsExactly("order.paid");
        assertThat(jdbc.queryForObject("select payload ->> 'orderId' from outbox where aggregate_id = ?",
                String.class, orderId)).isEqualTo(orderId.toString());
    }

    @Test
    void failedPayment_keepsOrderPending_andCanBeRetriedWithNewProviderPayment() throws Exception {
        var orderId = payments.placeOrder(buyer, event.eventId(), event.seat(2));
        var failed = payments.startPayment(buyer, orderId);

        mockMvc.perform(payments.webhook(failed, "FAILED")).andExpect(status().isOk());

        assertThat(paymentStatus(orderId)).isEqualTo("FAILED");
        assertThat(orderStatus(orderId)).isEqualTo("PENDING");
        assertThat(seatStatus(event.seat(2))).isEqualTo("RESERVED");

        var retry = payments.startPayment(buyer, orderId);
        assertThat(retry.providerPaymentId()).isNotEqualTo(failed.providerPaymentId());
        mockMvc.perform(payments.webhook(retry, "SUCCEEDED")).andExpect(status().isOk());
        assertThat(orderStatus(orderId)).isEqualTo("PAID");
    }

    @Test
    void forgedSignature_isRejected_andChangesNothing() throws Exception {
        var orderId = payments.placeOrder(buyer, event.eventId(), event.seat(3));
        var payment = payments.startPayment(buyer, orderId);

        mockMvc.perform(payments.webhook(payment, "SUCCEEDED").header("X-Signature", "sha256=" + "0".repeat(64)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(payments.webhook(payment.providerPaymentId(), "SUCCEEDED", payment.amount(),
                        Instant.now().minus(Duration.ofMinutes(10))))
                .andExpect(status().isUnauthorized());

        assertThat(orderStatus(orderId)).isEqualTo("PENDING");
    }

    @Test
    void wrongAmount_isRejected() throws Exception {
        var orderId = payments.placeOrder(buyer, event.eventId(), event.seat(4));
        var payment = payments.startPayment(buyer, orderId);

        mockMvc.perform(payments.webhook(payment.providerPaymentId(), "SUCCEEDED", new BigDecimal("1.00"), Instant.now()))
                .andExpect(status().isUnprocessableEntity());

        assertThat(orderStatus(orderId)).isEqualTo("PENDING");
        assertThat(paymentStatus(orderId)).isEqualTo("CREATED");
    }

    @Test
    void latePayment_afterExpiry_seatsStillFree_orderIsPaidAnyway() throws Exception {
        var orderId = payments.placeOrder(buyer, event.eventId(), event.seat(6));
        var payment = payments.startPayment(buyer, orderId);
        expire(orderId, event.seat(6));

        mockMvc.perform(payments.webhook(payment, "SUCCEEDED")).andExpect(status().isOk());

        assertThat(orderStatus(orderId)).isEqualTo("PAID");
        assertThat(seatStatus(event.seat(6))).isEqualTo("SOLD");
        assertThat(jdbc.queryForObject("select count(*) from order_items where order_id = ? and active",
                Integer.class, orderId)).isEqualTo(1);
    }

    @Test
    void latePayment_afterSeatWasSoldToSomeoneElse_isRefunded() throws Exception {
        var orderId = payments.placeOrder(buyer, event.eventId(), event.seat(7));
        var payment = payments.startPayment(buyer, orderId);
        expire(orderId, event.seat(7));
        // поки перший покупець «думав», місце купив інший
        payments.placeOrder(testData.user(Role.USER), event.eventId(), event.seat(7));

        mockMvc.perform(payments.webhook(payment, "SUCCEEDED")).andExpect(status().isOk());

        assertThat(orderStatus(orderId)).isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject("select refunded_at is not null from payments where order_id = ?",
                Boolean.class, orderId)).isTrue();
        assertThat(seatStatus(event.seat(7))).isEqualTo("RESERVED");
        assertThat(jdbc.queryForObject("select count(*) from outbox where aggregate_id = ?", Integer.class, orderId))
                .isZero();
    }

    @Test
    void paymentForCancelledOrder_isRefunded() throws Exception {
        var orderId = payments.placeOrder(buyer, event.eventId(), event.seat(8));
        var payment = payments.startPayment(buyer, orderId);
        mockMvc.perform(post("/api/v1/orders/{id}/cancel", orderId)
                        .header(HttpHeaders.AUTHORIZATION, testData.bearer(buyer)))
                .andExpect(status().isOk());

        mockMvc.perform(payments.webhook(payment, "SUCCEEDED")).andExpect(status().isOk());

        assertThat(orderStatus(orderId)).isEqualTo("REFUNDED");
        assertThat(seatStatus(event.seat(8))).isEqualTo("AVAILABLE");
    }

    /** Те, що зробив би ExpiredReservationJob: замовлення EXPIRED, місце повернулося в продаж. */
    private void expire(UUID orderId, UUID seatId) {
        jdbc.update("update orders set status = 'EXPIRED', expires_at = now() - interval '1 minute' where id = ?", orderId);
        jdbc.update("update order_items set active = false where order_id = ?", orderId);
        jdbc.update("update event_seats set status = 'AVAILABLE', reserved_until = null where id = ?", seatId);
        jdbc.update("delete from outbox where aggregate_id = ?", orderId);
        // і ключ у Redis уже протух
        testData.forgetSeatClaim(seatId);
    }

    private String orderStatus(UUID orderId) {
        return jdbc.queryForObject("select status from orders where id = ?", String.class, orderId);
    }

    private String paymentStatus(UUID orderId) {
        return jdbc.queryForObject("select status from payments where order_id = ?", String.class, orderId);
    }

    private String seatStatus(UUID seatId) {
        return jdbc.queryForObject("select status from event_seats where id = ?", String.class, seatId);
    }
}
