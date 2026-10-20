package com.tickethub.ticket.api;

import com.tickethub.support.IntegrationTestBase;
import com.tickethub.support.PaymentTestSupport;
import com.tickethub.support.TestData.PublishedEvent;
import com.tickethub.ticket.api.dto.ScanRequest;
import com.tickethub.ticket.service.TicketService;
import com.tickethub.user.domain.Role;
import com.tickethub.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TicketScanIT extends IntegrationTestBase {

    @Autowired
    private PaymentTestSupport payments;

    @Autowired
    private TicketService ticketService;

    @Autowired
    private JdbcTemplate jdbc;

    private PublishedEvent event;
    private String code;

    @BeforeEach
    void setUp() throws Exception {
        event = testData.publishedEvent();
        User buyer = testData.user(Role.USER);
        var orderId = payments.placeOrder(buyer, event.eventId(), event.seat(6));
        var payment = payments.startPayment(buyer, orderId);
        mockMvc.perform(payments.webhook(payment, "SUCCEEDED")).andExpect(status().isOk());
        ticketService.issueAndSend(orderId);
        code = jdbc.queryForObject("select code from tickets where order_id = ?", String.class, orderId);
    }

    @Test
    void firstScan_letsIn_secondScan_isRejectedWithTimeOfEntry() throws Exception {
        scan(testData.bearer(event.organizer()), event.eventId(), code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("USED"))
                .andExpect(jsonPath("$.section").value("Партер"))
                .andExpect(jsonPath("$.row").value(2))
                .andExpect(jsonPath("$.number").value(2));

        scan(testData.bearer(event.organizer()), event.eventId(), code)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.usedAt").exists());
    }

    @Test
    void organizerOfAnotherEvent_cannotScan() throws Exception {
        scan(testData.bearer(Role.ORGANIZER), event.eventId(), code).andExpect(status().isForbidden());
        scan(testData.bearer(Role.USER), event.eventId(), code).andExpect(status().isForbidden());
    }

    @Test
    void ticketForAnotherEvent_isRejectedAtTheDoor() throws Exception {
        var otherEvent = testData.publishedEvent();

        scan(testData.bearer(otherEvent.organizer()), otherEvent.eventId(), code)
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void unknownCode_returns404() throws Exception {
        scan(testData.bearer(event.organizer()), event.eventId(), "forged-" + UUID.randomUUID())
                .andExpect(status().isNotFound());
    }

    /** Два сканери на різних входах зчитали той самий QR в одну мить — пройти має лише одна людина. */
    @Test
    void simultaneousScans_letInExactlyOnce() throws Exception {
        var token = testData.bearer(event.organizer());
        var start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < 8; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return scan(token, event.eventId(), code).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (var result : results) {
                statuses.add(result.get());
            }
            assertThat(statuses).containsOnlyOnce(200).filteredOn(s -> s != 200).containsOnly(409);
        }
    }

    private ResultActions scan(String token, UUID eventId, String ticketCode) throws Exception {
        return mockMvc.perform(post("/api/v1/tickets/scan")
                .header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new ScanRequest(eventId, ticketCode))));
    }
}
