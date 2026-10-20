package com.tickethub.event.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record UpdateEventRequest(
        @NotBlank @Size(max = 255) String title,
        @Size(max = 10_000) String description,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt
) {
}
