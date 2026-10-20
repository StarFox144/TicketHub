package com.tickethub.ticket;

import com.tickethub.common.outbox.OutboxRelay;
import com.tickethub.support.IntegrationTestBase;
import com.tickethub.support.PaymentTestSupport;
import com.tickethub.support.TestData.PublishedEvent;
import com.tickethub.ticket.messaging.TicketMessagingConfig;
import com.tickethub.ticket.service.TicketService;
import com.tickethub.user.domain.Role;
import com.tickethub.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TicketIssuingIT extends IntegrationTestBase {

    @Autowired
    private PaymentTestSupport payments;

    @Autowired
    private OutboxRelay outboxRelay;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

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
    void paidOrder_travelsThroughOutboxAndRabbit_toTicketsAndEmail() throws Exception {
        var orderId = payAndConfirm(event.seat(0), event.seat(1));
        assertThat(ticketCount(orderId)).isZero(); // ще нічого: подія лежить в outbox

        outboxRelay.run();

        assertThat(jdbc.queryForObject("select published_at is not null from outbox where aggregate_id = ?",
                Boolean.class, orderId)).isTrue();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(mailpit().messagesTo(buyer.getEmail())).hasSize(1));
        assertThat(ticketCount(orderId)).isEqualTo(2);

        var mail = mailpit().messagesTo(buyer.getEmail()).getFirst();
        assertThat(mail.subject()).startsWith("Ваші квитки: ");
        assertThat(mail.attachments()).isEqualTo(2);
        assertThat(mailpit().html(mail.id())).contains("Партер", "VIP", "1000");

        mockMvc.perform(get("/api/v1/tickets").header(HttpHeaders.AUTHORIZATION, testData.bearer(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].status").value("VALID"));
    }

    @Test
    void pdf_isAvailableToOwnerOnly() throws Exception {
        var orderId = payAndConfirm(event.seat(2));
        ticketService.issueAndSend(orderId);
        var ticketId = jdbc.queryForObject("select id from tickets where order_id = ?", UUID.class, orderId);

        var pdf = mockMvc.perform(get("/api/v1/tickets/{id}/pdf", ticketId)
                        .header(HttpHeaders.AUTHORIZATION, testData.bearer(buyer)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        mockMvc.perform(get("/api/v1/tickets/{id}/pdf", ticketId)
                        .header(HttpHeaders.AUTHORIZATION, testData.bearer(Role.USER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void redelivery_doesNotDuplicateTickets() {
        var orderId = payAndConfirm(event.seat(3));

        ticketService.issueAndSend(orderId);
        var firstCode = jdbc.queryForObject("select code from tickets where order_id = ?", String.class, orderId);
        ticketService.issueAndSend(orderId);

        assertThat(ticketCount(orderId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select code from tickets where order_id = ?", String.class, orderId))
                .isEqualTo(firstCode);
    }

    @Test
    void poisonMessage_endsUpInDeadLetterQueue() {
        var poison = MessageBuilder.withBody("not json at all".getBytes(StandardCharsets.UTF_8))
                .setContentType("application/json")
                .setMessageId(UUID.randomUUID().toString())
                .build();

        rabbitTemplate.send("tickets", "order.paid", poison);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            var dead = rabbitTemplate.receive(TicketMessagingConfig.DEAD_LETTER_QUEUE, 500);
            assertThat(dead).isNotNull();
            assertThat(new String(dead.getBody(), StandardCharsets.UTF_8)).isEqualTo("not json at all");
        });
    }

    private UUID payAndConfirm(UUID... seats) {
        var orderId = payments.placeOrder(buyer, event.eventId(), seats);
        var payment = payments.startPayment(buyer, orderId);
        try {
            mockMvc.perform(payments.webhook(payment, "SUCCEEDED")).andExpect(status().isOk());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return orderId;
    }

    private int ticketCount(UUID orderId) {
        return jdbc.queryForObject("select count(*) from tickets where order_id = ?", Integer.class, orderId);
    }
}
