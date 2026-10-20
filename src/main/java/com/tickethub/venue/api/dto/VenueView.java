package com.tickethub.venue.api.dto;

import java.util.List;
import java.util.UUID;

public record VenueView(UUID id, String name, String city, String address, int capacity, List<SectionView> sections) {

    public record SectionView(UUID id, String name, int rowsCount, int seatsCount) {
    }
}
