package com.tickethub.event.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Секція цілком або діапазон її рядів (межі включно). */
public record SeatRangeDto(@NotNull UUID sectionId, @Min(1) Integer rowFrom, @Min(1) Integer rowTo) {

    public static SeatRangeDto wholeSection(UUID sectionId) {
        return new SeatRangeDto(sectionId, null, null);
    }
}
