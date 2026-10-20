package com.tickethub.venue.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Секція з прямокутною сіткою місць: rowsCount рядів по seatsPerRow місць. */
public record CreateSectionRequest(
        @NotBlank @Size(max = 100) String name,
        @Min(1) @Max(200) int rowsCount,
        @Min(1) @Max(200) int seatsPerRow
) {
}
