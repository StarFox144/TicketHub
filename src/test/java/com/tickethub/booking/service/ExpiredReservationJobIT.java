package com.tickethub.booking.service;

import com.tickethub.booking.api.dto.CreateOrderRequest;
import com.tickethub.support.IntegrationTestBase;
import com.tickethub.support.TestData.PublishedEvent;
import com.tickethub.user.domain.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ExpiredReservationJobIT extends IntegrationTestBase {

    @Autowired
    private ExpiredReservationJob job;

    @Autowired
    private OrderService orderService;

    @Autowired
    private JdbcTemplate jdbc;

    private PublishedEvent event;

    @BeforeEach
    void setUp() {
        event = testData.publishedEvent();
    }

    @Test
    void expiredOrder_isExpired_andSeatsReturnToSale() {
        var expired = placeOrder(event.seat(0), event.seat(1));
        var fresh = placeOrder(event.seat(2));
        jdbc.update("update orders set expires_at = now() - interval '1 minute' where id = ?", expired);

        job.run();

        assertThat(orderStatus(expired)).isEqualTo("EXPIRED");
        assertThat(orderStatus(fresh)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select count(*) from order_items where order_id = ? and active",
                Integer.class, expired)).isZero();
        assertThat(seatStatus(event.seat(0))).isEqualTo("AVAILABLE");
        assertThat(seatStatus(event.seat(1))).isEqualTo("AVAILABLE");
        assertThat(seatStatus(event.seat(2))).isEqualTo("RESERVED");
        assertThat(jdbc.queryForObject("select reserved_until from event_seats where id = ?",
                Object.class, event.seat(0))).isNull();

        // звільнене місце знову можна купити
        placeOrder(event.seat(0));
        assertThat(seatStatus(event.seat(0))).isEqualTo("RESERVED");
    }

    @Test
    void run_isIdempotent() {
        var expired = placeOrder(event.seat(3));
        jdbc.update("update orders set expires_at = now() - interval '1 minute' where id = ?", expired);

        job.run();
        job.run();

        assertThat(orderStatus(expired)).isEqualTo("EXPIRED");
    }

    private UUID placeOrder(UUID... seatIds) {
        var buyer = testData.user(Role.USER);
        return orderService.placeOrder(buyer.getId(),
                new CreateOrderRequest(event.eventId(), List.of(seatIds)), UUID.randomUUID().toString())
                .order().orderId();
    }

    private String orderStatus(UUID orderId) {
        return jdbc.queryForObject("select status from orders where id = ?", String.class, orderId);
    }

    private String seatStatus(UUID eventSeatId) {
        return jdbc.queryForObject("select status from event_seats where id = ?", String.class, eventSeatId);
    }
}
