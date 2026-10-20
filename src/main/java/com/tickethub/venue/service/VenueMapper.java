package com.tickethub.venue.service;

import com.tickethub.venue.api.dto.VenueSummary;
import com.tickethub.venue.api.dto.VenueView;
import com.tickethub.venue.api.dto.VenueView.SectionView;
import com.tickethub.venue.domain.Venue;
import org.mapstruct.Mapper;

import java.util.Map;
import java.util.UUID;

@Mapper
public interface VenueMapper {

    VenueSummary toSummary(Venue venue);

    /** Кількість місць передаємо окремо, щоб не підвантажувати всі Seat заради підрахунку. */
    default VenueView toView(Venue venue, Map<UUID, Integer> seatsBySection) {
        var sections = venue.getSections().stream()
                .map(s -> new SectionView(s.getId(), s.getName(), s.getRowsCount(),
                        seatsBySection.getOrDefault(s.getId(), 0)))
                .toList();
        int capacity = sections.stream().mapToInt(SectionView::seatsCount).sum();
        return new VenueView(venue.getId(), venue.getName(), venue.getCity(), venue.getAddress(), capacity, sections);
    }
}
