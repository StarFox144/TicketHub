package com.tickethub.event.api.dto;

import com.tickethub.event.domain.EventStatus;

import java.time.Instant;
import java.util.UUID;

public record EventSummary(
        UUID id,
        String title,
        Instant startsAt,
        Instant endsAt,
        EventStatus status,
        String venueName,
        String city
) {
}
