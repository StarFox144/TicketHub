package com.tickethub.event.api.dto;

import com.tickethub.event.domain.SeatStatus;

import java.math.BigDecimal;
import java.util.UUID;

/** Місце на карті залу. id — це eventSeatId, саме його передають у POST /orders. */
public record SeatView(
        UUID id,
        String section,
        int row,
        int number,
        String zone,
        BigDecimal price,
        String currency,
        SeatStatus status
) {
}
