package com.tickethub.venue.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateVenueRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 255) String address,
        @NotEmpty @Size(max = 20) List<@Valid CreateSectionRequest> sections
) {
}
