package com.tickethub.event.api;

import com.tickethub.booking.api.dto.CreateOrderRequest;
import com.tickethub.booking.service.OrderService;
import com.tickethub.support.IntegrationTestBase;
import com.tickethub.support.TestData.PublishedEvent;
import com.tickethub.user.domain.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeatMapCacheIT extends IntegrationTestBase {

    @Autowired
    private OrderService orderService;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcTemplate jdbc;

    private PublishedEvent event;

    @BeforeEach
    void setUp() {
        event = testData.publishedEvent();
        redis.delete("seat-map::" + event.eventId()); // TestData.publishedEvent() уже прочитав карту
    }

    @Test
    void seatMap_isServedFromCache_untilSeatStatusChanges() throws Exception {
        assertThat(statusOf(event.seat(0))).isEqualTo("AVAILABLE");
        assertThat(redis.hasKey("seat-map::" + event.eventId())).isTrue();

        // зміна в обхід сервісів не інвалідує кеш — відповідь ще стара, тобто справді з кешу
        jdbc.update("update event_seats set status = 'BLOCKED' where id = ?", event.seat(9));
        assertThat(statusOf(event.seat(9))).isEqualTo("AVAILABLE");

        // резерв через сервіс скидає кеш після коміту
        var buyer = testData.user(Role.USER);
        orderService.placeOrder(buyer.getId(),
                new CreateOrderRequest(event.eventId(), List.of(event.seat(0))), UUID.randomUUID().toString());

        assertThat(statusOf(event.seat(0))).isEqualTo("RESERVED");
        assertThat(statusOf(event.seat(9))).isEqualTo("BLOCKED");
    }

    @Test
    void cachedEntry_hasShortTtl() throws Exception {
        statusOf(event.seat(0));

        assertThat(redis.getExpire("seat-map::" + event.eventId())).isBetween(1L, 3L);
    }

    private String statusOf(UUID seatId) throws Exception {
        var body = mockMvc.perform(get("/api/v1/events/{id}/seats", event.eventId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (var seat : objectMapper.readTree(body)) {
            if (seat.get("id").asText().equals(seatId.toString())) {
                return seat.get("status").asText();
            }
        }
        throw new AssertionError("Seat %s is missing from the seat map".formatted(seatId));
    }
}
