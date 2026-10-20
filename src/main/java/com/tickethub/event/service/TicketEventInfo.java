package com.tickethub.event.service;

import com.tickethub.event.api.dto.SeatView;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Усе, що модулю ticket треба знати про подію і місця, щоб надрукувати квиток. */
public record TicketEventInfo(
        UUID eventId,
        String title,
        Instant startsAt,
        String venueName,
        String venueAddress,
        String city,
        Map<UUID, SeatView> seats
) {

    public SeatView seat(UUID eventSeatId) {
        var seat = seats.get(eventSeatId);
        if (seat == null) {
            throw new IllegalArgumentException("Seat %s does not belong to event %s".formatted(eventSeatId, eventId));
        }
        return seat;
    }
}
