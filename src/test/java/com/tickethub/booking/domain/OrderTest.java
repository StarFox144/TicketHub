package com.tickethub.booking.domain;

import com.tickethub.common.exception.BusinessRuleException;
import com.tickethub.common.exception.ConflictException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");
    private static final UUID EVENT = UUID.randomUUID();
    private static final UUID SEAT_A = UUID.randomUUID();
    private static final UUID SEAT_B = UUID.randomUUID();

    @Test
    void create_sumsPricesOfAllSeats() {
        var order = newOrder();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("1700.00");
        assertThat(order.getCurrency()).isEqualTo("UAH");
        assertThat(order.getItems()).allMatch(OrderItem::isActive);
    }

    @Test
    void create_rejectsMixedCurrencies() {
        assertThatThrownBy(() -> Order.create(UUID.randomUUID(), EVENT, List.of(
                new Order.Line(SEAT_A, BigDecimal.TEN, "UAH"),
                new Order.Line(SEAT_B, BigDecimal.TEN, "EUR")), "key", NOW))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void cancel_deactivatesItems_andCanHappenOnlyOnce() {
        var order = newOrder();

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getItems()).noneMatch(OrderItem::isActive);
        assertThatThrownBy(order::cancel).isInstanceOf(ConflictException.class);
        assertThatThrownBy(order::expire).isInstanceOf(ConflictException.class);
    }

    @Test
    void remaining_countsDownToZero() {
        var order = newOrder();

        assertThat(order.remaining(NOW)).isEqualTo(Duration.ofMinutes(10));
        assertThat(order.remaining(NOW.plus(Duration.ofMinutes(11)))).isZero();
    }

    @Test
    void matchesRequest_ignoresSeatOrder() {
        var order = newOrder();

        assertThat(order.matchesRequest(EVENT, List.of(SEAT_B, SEAT_A))).isTrue();
        assertThat(order.matchesRequest(EVENT, List.of(SEAT_A))).isFalse();
        assertThat(order.matchesRequest(UUID.randomUUID(), List.of(SEAT_A, SEAT_B))).isFalse();
    }

    private static Order newOrder() {
        return Order.create(UUID.randomUUID(), EVENT, List.of(
                new Order.Line(SEAT_A, new BigDecimal("1200.00"), "UAH"),
                new Order.Line(SEAT_B, new BigDecimal("500.00"), "UAH")), "key-1", NOW.plus(Duration.ofMinutes(10)));
    }
}
