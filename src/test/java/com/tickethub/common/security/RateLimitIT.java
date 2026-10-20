package com.tickethub.common.security;

import com.tickethub.booking.api.dto.CreateOrderRequest;
import com.tickethub.support.IntegrationTestBase;
import com.tickethub.support.TestData;
import com.tickethub.support.TestData.PublishedEvent;
import com.tickethub.user.api.dto.LoginRequest;
import com.tickethub.user.domain.Role;
import com.tickethub.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "tickethub.rate-limit.login.capacity=3",
        "tickethub.rate-limit.orders.capacity=2"})
class RateLimitIT extends IntegrationTestBase {

    private User user;
    /** Бакети живуть у спільному Redis — кожен тест має власну «адресу», щоб не ділити ліміт. */
    private String clientIp;

    @BeforeEach
    void setUp() {
        user = testData.user(Role.USER);
        var random = ThreadLocalRandom.current();
        clientIp = "10.%d.%d.%d".formatted(random.nextInt(256), random.nextInt(256), random.nextInt(1, 255));
    }

    @Test
    void login_isLimitedPerIp_evenForWrongPasswords() throws Exception {
        login(clientIp, TestData.PASSWORD).andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Remaining", "2"));
        login(clientIp, "wrong").andExpect(status().isUnauthorized());
        login(clientIp, "wrong").andExpect(status().isUnauthorized());

        login(clientIp, TestData.PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.title").value("Too Many Requests"));

        // інша адреса має власний ліміт
        login("192.168.77.1", TestData.PASSWORD).andExpect(status().isOk());
    }

    @Test
    void orders_areLimitedPerUser() throws Exception {
        PublishedEvent event = testData.publishedEvent();
        var token = testData.bearer(user);

        order(token, event, event.seat(0)).andExpect(status().isCreated());
        order(token, event, event.seat(1)).andExpect(status().isCreated());
        order(token, event, event.seat(2)).andExpect(status().isTooManyRequests());

        // інший користувач не страждає через сусіда-скальпера
        order(testData.bearer(Role.USER), event, event.seat(2)).andExpect(status().isCreated());
    }

    private ResultActions login(String ip, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new LoginRequest(user.getEmail(), password))));
    }

    private ResultActions order(String token, PublishedEvent event, UUID seat) throws Exception {
        return mockMvc.perform(post("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new CreateOrderRequest(event.eventId(), List.of(seat)))));
    }
}
