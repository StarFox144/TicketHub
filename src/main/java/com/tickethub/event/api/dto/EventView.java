package com.tickethub.event.api.dto;

import com.tickethub.event.domain.EventStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EventView(
        UUID id,
        String title,
        String description,
        Instant startsAt,
        Instant endsAt,
        EventStatus status,
        UUID organizerId,
        VenueRef venue,
        List<PriceZoneView> priceZones
) {

    public record VenueRef(UUID id, String name, String city, String address) {
    }

    public record PriceZoneView(UUID id, String name, BigDecimal price, String currency, List<SeatRangeDto> ranges) {
    }
}
