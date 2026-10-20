package com.tickethub.venue.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateVenueRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 255) String address
) {
}
