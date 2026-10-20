package com.tickethub;

import com.tickethub.booking.api.dto.CreateOrderRequest;
import com.tickethub.common.outbox.OutboxRelay;
import com.tickethub.support.IntegrationTestBase;
import com.tickethub.support.TestData.PublishedEvent;
import com.tickethub.ticket.api.dto.ScanRequest;
import com.tickethub.user.domain.Role;
import com.tickethub.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Основний сценарій зі специфікації від початку до кінця:
 * вибір місця → резерв → оплата у провайдера → webhook (справжній HTTP-запит від провайдера на запущений сервер)
 * → outbox → RabbitMQ → квиток з QR у PDF → лист у Mailpit → сканування на вході.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PurchaseFlowE2EIT extends IntegrationTestBase {

    @Autowired
    private OutboxRelay outboxRelay;

    @Test
    void buyerPays_getsTicketByEmail_andEntersTheVenue() throws Exception {
        PublishedEvent event = testData.publishedEvent();
        User buyer = testData.user(Role.USER);
        var buyerToken = testData.bearer(buyer);

        // 1. резерв місця
        var order = mockMvc.perform(post("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, buyerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new CreateOrderRequest(event.eventId(), List.of(event.seat(0))))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var orderId = objectMapper.readTree(order).get("orderId").asText();

        // 2. платіж — отримуємо сторінку провайдера
        var payment = mockMvc.perform(post("/api/v1/orders/{id}/pay", orderId)
                        .header(HttpHeaders.AUTHORIZATION, buyerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var checkoutPath = URI.create(objectMapper.readTree(payment).get("checkoutUrl").asText()).getPath();

        mockMvc.perform(get(checkoutPath))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("1000.00 UAH")));

        // 3. покупець натискає «Оплатити»: провайдер шле підписаний webhook на запущений сервер
        mockMvc.perform(post(checkoutPath + "/complete").param("outcome", "success"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("HTTP 200")));

        mockMvc.perform(get("/api/v1/orders/{id}", orderId).header(HttpHeaders.AUTHORIZATION, buyerToken))
                .andExpect(jsonPath("$.status").value("PAID"));

        // 4. outbox → RabbitMQ → квиток і лист
        outboxRelay.run();
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(mailpit().messagesTo(buyer.getEmail())).singleElement()
                        .satisfies(mail -> assertThat(mail.attachments()).isEqualTo(1)));

        var tickets = mockMvc.perform(get("/api/v1/tickets").header(HttpHeaders.AUTHORIZATION, buyerToken))
                .andExpect(jsonPath("$.content[0].status").value("VALID"))
                .andReturn().getResponse().getContentAsString();
        var code = objectMapper.readTree(tickets).at("/content/0/code").asText();

        // 5. вхід: організатор сканує QR
        var organizerToken = testData.bearer(event.organizer());
        mockMvc.perform(post("/api/v1/tickets/scan")
                        .header(HttpHeaders.AUTHORIZATION, organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ScanRequest(event.eventId(), code))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("USED"));

        // і місце на карті залу тепер продане
        mockMvc.perform(get("/api/v1/events/{id}/seats", event.eventId()))
                .andExpect(jsonPath("$[0].status").value("SOLD"));
    }
}
