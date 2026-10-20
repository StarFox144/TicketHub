package com.tickethub.common;

import com.tickethub.booking.api.dto.CreateOrderRequest;
import com.tickethub.support.IntegrationTestBase;
import com.tickethub.support.TestData;
import com.tickethub.support.TestData.PublishedEvent;
import com.tickethub.user.api.dto.LoginRequest;
import com.tickethub.user.domain.Role;
import com.tickethub.user.domain.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Redis — лише прискорювач: коли він «висне», продаж має тривати через Postgres.
 * Контейнер ставиться на паузу (а не зупиняється) — команди падають за таймаутом, як при реальному збої мережі.
 */
class RedisOutageIT extends IntegrationTestBase {

    private PublishedEvent event;
    private User buyer;

    @BeforeEach
    void setUp() {
        event = testData.publishedEvent();
        buyer = testData.user(Role.USER);
        pauseRedis();
    }

    @AfterEach
    void tearDown() {
        unpauseRedis();
    }

    @Test
    void salesContinue_whenRedisIsDown() throws Exception {
        order(event.seat(0)).andExpect(status().isCreated());
        // зайнятість місця тепер перевіряє лише БД — і все одно коректно
        order(event.seat(0)).andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/events/{id}/seats", event.eventId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("RESERVED"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(buyer.getEmail(), TestData.PASSWORD))))
                .andExpect(status().isOk());
    }

    private ResultActions order(UUID seat) throws Exception {
        return mockMvc.perform(post("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, testData.bearer(buyer))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new CreateOrderRequest(event.eventId(), List.of(seat)))));
    }
}
