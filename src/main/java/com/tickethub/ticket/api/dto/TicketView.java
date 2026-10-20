package com.tickethub.ticket.api.dto;

import com.tickethub.ticket.domain.TicketStatus;

import java.time.Instant;
import java.util.UUID;

public record TicketView(
        UUID id,
        UUID orderId,
        UUID eventId,
        UUID eventSeatId,
        String code,
        TicketStatus status,
        Instant usedAt,
        Instant createdAt
) {
}
