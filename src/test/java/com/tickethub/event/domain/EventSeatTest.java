package com.tickethub.event.domain;

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

class EventSeatTest {

    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(10);

    @Test
    void reserve_setsStatusAndDeadline() {
        var seat = newSeat();

        seat.reserve(TTL, NOW);

        assertThat(seat.getStatus()).isEqualTo(SeatStatus.RESERVED);
        assertThat(seat.getReservedUntil()).isEqualTo(NOW.plus(TTL));
    }

    @Test
    void reserve_twice_isRejected() {
        var seat = newSeat();
        seat.reserve(TTL, NOW);

        assertThatThrownBy(() -> seat.reserve(TTL, NOW))
                .isInstanceOf(SeatsUnavailableException.class)
                .extracting(e -> ((SeatsUnavailableException) e).getUnavailableSeatIds())
                .isEqualTo(List.of(seat.getId()));
    }

    @Test
    void release_returnsSeatToSale() {
        var seat = newSeat();
        seat.reserve(TTL, NOW);

        seat.release();

        assertThat(seat.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(seat.getReservedUntil()).isNull();
    }

    @Test
    void release_doesNotTouchSoldSeat() {
        var seat = newSeat();
        seat.reserve(TTL, NOW);
        seat.markSold();

        seat.release();

        assertThat(seat.getStatus()).isEqualTo(SeatStatus.SOLD);
    }

    @Test
    void markSold_requiresReservation() {
        var seat = newSeat();

        assertThatThrownBy(seat::markSold).isInstanceOf(IllegalStateException.class);
    }

    private static EventSeat newSeat() {
        var venue = new Venue("Hall", "Київ", "Street 1");
        var section = venue.addSection("Партер", 1, 1);
        var organizer = new User("org@test.local", "hash", "Org", Role.ORGANIZER);
        var event = new Event(venue, organizer, "Concert", null,
                NOW.plus(Duration.ofDays(1)), NOW.plus(Duration.ofDays(1).plusHours(2)), NOW);
        event.replacePriceZones(List.of(new Event.PriceZoneSpec("Стандарт", BigDecimal.TEN, "UAH",
                List.of(new SeatRange(section.getId(), null, null)))));
        return event.publish(section.getSeats(), NOW).getFirst();
    }
}
