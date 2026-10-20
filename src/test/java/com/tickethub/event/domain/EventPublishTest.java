package com.tickethub.event.domain;

import com.tickethub.common.exception.BusinessRuleException;
import com.tickethub.common.exception.ConflictException;
import com.tickethub.event.domain.Event.PriceZoneSpec;
import com.tickethub.user.domain.Role;
import com.tickethub.user.domain.User;
import com.tickethub.venue.domain.Seat;
import com.tickethub.venue.domain.Section;
import com.tickethub.venue.domain.Venue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

class EventPublishTest {

    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

    private Venue venue;
    private Section stalls;
    private Section balcony;
    private Event event;

    @BeforeEach
    void setUp() {
        venue = new Venue("Hall", "Київ", "Street 1");
        stalls = venue.addSection("Партер", 5, 4);   // 20 місць
        balcony = venue.addSection("Балкон", 2, 5);  // 10 місць
        var organizer = new User("org@test.local", "hash", "Org", Role.ORGANIZER);
        event = new Event(venue, organizer, "Concert", null,
                NOW.plus(Duration.ofDays(1)), NOW.plus(Duration.ofDays(1).plusHours(2)), NOW);
    }

    @Test
    void publish_createsEventSeatPerCoveredSeat_withMatchingZone() {
        event.replacePriceZones(List.of(
                zone("VIP", "1000", new SeatRange(stalls.getId(), 1, 2)),
                zone("Стандарт", "500", new SeatRange(stalls.getId(), 3, null)),
                zone("Балкон", "300", new SeatRange(balcony.getId(), null, null))));

        var seats = event.publish(allSeats(), NOW);

        assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(seats).hasSize(30).allMatch(s -> s.getStatus() == SeatStatus.AVAILABLE);
        var countByZone = seats.stream()
                .collect(Collectors.groupingBy(s -> s.getPriceZone().getName(), Collectors.counting()));
        assertThat(countByZone).containsOnly(entry("VIP", 8L), entry("Стандарт", 12L), entry("Балкон", 10L));
    }

    @Test
    void publish_skipsSeatsOutsideZones() {
        event.replacePriceZones(List.of(zone("Балкон", "300", new SeatRange(balcony.getId(), 1, 1))));

        assertThat(event.publish(allSeats(), NOW)).hasSize(5);
    }

    @Test
    void publish_withoutCoveredSeats_isRejected() {
        assertThatThrownBy(() -> event.publish(allSeats(), NOW))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
    }

    @Test
    void publish_twice_isRejected() {
        event.replacePriceZones(List.of(zone("Балкон", "300", new SeatRange(balcony.getId(), null, null))));
        event.publish(allSeats(), NOW);

        assertThatThrownBy(() -> event.publish(allSeats(), NOW)).isInstanceOf(ConflictException.class);
    }

    @Test
    void overlappingZones_areRejected() {
        assertThatThrownBy(() -> event.replacePriceZones(List.of(
                zone("VIP", "1000", new SeatRange(stalls.getId(), 1, 3)),
                zone("Стандарт", "500", new SeatRange(stalls.getId(), 3, 5)))))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("overlap");
    }

    @Test
    void wholeSection_overlapsAnyRangeOfSameSection() {
        assertThatThrownBy(() -> event.replacePriceZones(List.of(
                zone("Усе", "500", new SeatRange(stalls.getId(), null, null)),
                zone("VIP", "1000", new SeatRange(stalls.getId(), 5, 5)))))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void invertedRange_isRejected() {
        assertThatThrownBy(() -> new SeatRange(stalls.getId(), 5, 2)).isInstanceOf(BusinessRuleException.class);
    }

    private List<Seat> allSeats() {
        return venue.getSections().stream().flatMap(s -> s.getSeats().stream()).toList();
    }

    private static PriceZoneSpec zone(String name, String price, SeatRange... ranges) {
        return new PriceZoneSpec(name, new BigDecimal(price), "UAH", List.of(ranges));
    }
}
