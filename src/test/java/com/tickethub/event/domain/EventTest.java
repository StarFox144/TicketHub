package com.tickethub.event.domain;

import com.tickethub.common.exception.BusinessRuleException;
import com.tickethub.event.domain.Event.PriceZoneSpec;
import com.tickethub.user.domain.Role;
import com.tickethub.user.domain.User;
import com.tickethub.venue.domain.Venue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class EventTest {

    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");
    private static final Instant STARTS = NOW.plus(Duration.ofDays(10));
    private static final Instant ENDS = STARTS.plus(Duration.ofHours(3));

    private final Venue venue = new Venue("Hall", "Київ", "Street 1");
    private final User organizer = new User("org@test.local", "hash", "Org", Role.ORGANIZER);

    @Test
    void newEvent_isDraft() {
        var event = new Event(venue, organizer, "Concert", null, STARTS, ENDS, NOW);

        assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
    }

    @Test
    void rejectsEndBeforeStart() {
        assertThatThrownBy(() -> new Event(venue, organizer, "Concert", null, STARTS, STARTS, NOW))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("end after");
    }

    @Test
    void rejectsStartInThePast() {
        var past = NOW.minusSeconds(60);
        assertThatThrownBy(() -> new Event(venue, organizer, "Concert", null, past, ENDS, NOW))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("future");
    }

    @Test
    void replacePriceZones_upsertsByName() {
        var event = new Event(venue, organizer, "Concert", null, STARTS, ENDS, NOW);
        event.replacePriceZones(List.of(
                new PriceZoneSpec("VIP", new BigDecimal("1000.00"), "UAH", List.of()),
                new PriceZoneSpec("Стандарт", new BigDecimal("500.00"), "UAH", List.of())));
        var vipBefore = event.getPriceZones().getFirst();

        event.replacePriceZones(List.of(
                new PriceZoneSpec("VIP", new BigDecimal("1500.00"), "UAH", List.of()),
                new PriceZoneSpec("Балкон", new BigDecimal("300.00"), "UAH", List.of())));

        assertThat(event.getPriceZones())
                .extracting(PriceZone::getName, PriceZone::getPrice)
                .containsExactlyInAnyOrder(
                        tuple("VIP", new BigDecimal("1500.00")),
                        tuple("Балкон", new BigDecimal("300.00")));
        assertThat(event.getPriceZones()).contains(vipBefore);
    }

    @Test
    void replacePriceZones_rejectsDuplicateNames() {
        var event = new Event(venue, organizer, "Concert", null, STARTS, ENDS, NOW);

        assertThatThrownBy(() -> event.replacePriceZones(List.of(
                new PriceZoneSpec("VIP", BigDecimal.TEN, "UAH", List.of()),
                new PriceZoneSpec("VIP", BigDecimal.ONE, "UAH", List.of()))))
                .isInstanceOf(BusinessRuleException.class);
    }
}
