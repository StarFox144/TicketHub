package com.tickethub.venue.api.dto;

import java.util.UUID;

public record VenueSummary(UUID id, String name, String city, String address) {
}
