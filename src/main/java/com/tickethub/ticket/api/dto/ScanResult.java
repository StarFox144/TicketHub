package com.tickethub.ticket.api.dto;

import com.tickethub.ticket.domain.TicketStatus;

import java.time.Instant;
import java.util.UUID;

/** Що бачить контролер на екрані сканера: куди сідати власнику квитка. */
public record ScanResult(
        UUID ticketId,
        TicketStatus status,
        Instant usedAt,
        String section,
        int row,
        int number
) {
}
