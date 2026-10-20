package com.tickethub.booking.service;

import com.tickethub.booking.api.dto.CreateOrderRequest;
import com.tickethub.event.domain.SeatsUnavailableException;
import com.tickethub.support.IntegrationTestBase;
import com.tickethub.support.TestData.PublishedEvent;
import com.tickethub.user.domain.Role;
import com.tickethub.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SeatClaimIT extends IntegrationTestBase {

    @Autowired
    private OrderService orderService;

    @Autowired
    private StringRedisTemplate redis;

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
    void successfulOrder_leavesClaimWithTtl_andCancelRemovesIt() {
        var key = UUID.randomUUID().toString();
        var order = placeOrder(buyer, key, event.seat(0));

        var redisKey = "seat:" + event.seat(0);
        assertThat(redis.opsForValue().get(redisKey)).isEqualTo(SeatClaimService.tokenFor(buyer.getId(), key));
        assertThat(redis.getExpire(redisKey)).isBetween(590L, 600L);

        orderService.cancel(order, buyer.getId());

        assertThat(redis.hasKey(redisKey)).isFalse();
    }

    @Test
    void seatClaimedInRedis_isRejectedWithoutTouchingDatabase() {
        // хтось інший уже застовпив місце, а в БД воно ще AVAILABLE (його транзакція в процесі)
        redis.opsForValue().set("seat:" + event.seat(1), "someone-else", Duration.ofMinutes(1));

        assertThatThrownBy(() -> placeOrder(buyer, UUID.randomUUID().toString(), event.seat(1), event.seat(2)))
                .isInstanceOf(SeatsUnavailableException.class)
                .extracting(e -> ((SeatsUnavailableException) e).getUnavailableSeatIds())
                .isEqualTo(List.of(event.seat(1)));

        // відсіяно в Redis: БД не змінилась, сусіднє місце ніхто не застовпив
        assertThat(seatStatus(event.seat(1))).isEqualTo("AVAILABLE");
        assertThat(redis.hasKey("seat:" + event.seat(2))).isFalse();
        assertThat(redis.opsForValue().get("seat:" + event.seat(1))).isEqualTo("someone-else");
    }

    @Test
    void databaseRejection_releasesFreshClaims() {
        // Redis «не знає» про резерв (наприклад, ключ протух), але БД — джерело правди
        jdbc.update("update event_seats set status = 'RESERVED' where id = ?", event.seat(3));

        assertThatThrownBy(() -> placeOrder(buyer, UUID.randomUUID().toString(), event.seat(3), event.seat(4)))
                .isInstanceOf(SeatsUnavailableException.class);

        // ключі, які цей запит щойно поставив, прибрано — інакше місце 4 хибно блокувалося б до TTL
        assertThat(redis.hasKey("seat:" + event.seat(3))).isFalse();
        assertThat(redis.hasKey("seat:" + event.seat(4))).isFalse();
    }

    @Test
    void retryWithSameKey_doesNotTripOverOwnClaims() {
        var key = UUID.randomUUID().toString();
        var first = placeOrder(buyer, key, event.seat(5));

        var second = placeOrder(buyer, key, event.seat(5));

        assertThat(second).isEqualTo(first);
    }

    private UUID placeOrder(User user, String key, UUID... seatIds) {
        return orderService.placeOrder(user.getId(), new CreateOrderRequest(event.eventId(), List.of(seatIds)), key)
                .order().orderId();
    }

    private String seatStatus(UUID eventSeatId) {
        return jdbc.queryForObject("select status from event_seats where id = ?", String.class, eventSeatId);
    }
}
