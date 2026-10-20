package com.tickethub.venue.service;

import com.tickethub.common.exception.NotFoundException;
import com.tickethub.venue.api.dto.CreateVenueRequest;
import com.tickethub.venue.api.dto.UpdateVenueRequest;
import com.tickethub.venue.api.dto.VenueSummary;
import com.tickethub.venue.api.dto.VenueView;
import com.tickethub.venue.domain.Seat;
import com.tickethub.venue.domain.Section;
import com.tickethub.venue.domain.Venue;
import com.tickethub.venue.repository.SeatRepository;
import com.tickethub.venue.repository.SeatRepository.SectionSeatCount;
import com.tickethub.venue.repository.VenueRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class VenueService {

    private final VenueRepository venueRepository;
    private final SeatRepository seatRepository;
    private final VenueMapper mapper;

    public VenueService(VenueRepository venueRepository, SeatRepository seatRepository, VenueMapper mapper) {
        this.venueRepository = venueRepository;
        this.seatRepository = seatRepository;
        this.mapper = mapper;
    }

    @Transactional
    public VenueView create(CreateVenueRequest request) {
        var venue = new Venue(request.name(), request.city(), request.address());
        request.sections().forEach(s -> venue.addSection(s.name(), s.rowsCount(), s.seatsPerRow()));
        venueRepository.save(venue);

        Map<UUID, Integer> seatCounts = venue.getSections().stream()
                .collect(Collectors.toMap(Section::getId, Section::seatsCount));
        return mapper.toView(venue, seatCounts);
    }

    @Transactional
    public VenueView update(UUID id, UpdateVenueRequest request) {
        var venue = getVenue(id);
        venue.update(request.name(), request.city(), request.address());
        return toView(venue);
    }

    public VenueView get(UUID id) {
        var venue = venueRepository.findWithSectionsById(id).orElseThrow(() -> new NotFoundException("Venue", id));
        return toView(venue);
    }

    public Page<VenueSummary> list(String city, Pageable pageable) {
        var page = city == null || city.isBlank()
                ? venueRepository.findAll(pageable)
                : venueRepository.findByCityIgnoreCase(city, pageable);
        return page.map(mapper::toSummary);
    }

    /** Для інших модулів (event): отримати зал як сутність для зв'язку. */
    public Venue getVenue(UUID id) {
        return venueRepository.findById(id).orElseThrow(() -> new NotFoundException("Venue", id));
    }

    /** Для модуля event: усі фізичні місця залу разом із секціями (публікація події). */
    public List<Seat> getSeats(UUID venueId) {
        return seatRepository.findAllByVenueId(venueId);
    }

    /** Для модуля event: перевірити, що секції з цінових зон належать залу події. */
    public Set<UUID> getSectionIds(UUID venueId) {
        return getVenue(venueId).getSections().stream().map(Section::getId).collect(Collectors.toSet());
    }

    private VenueView toView(Venue venue) {
        Map<UUID, Integer> seatCounts = seatRepository.countBySectionForVenue(venue.getId()).stream()
                .collect(Collectors.toMap(SectionSeatCount::getSectionId, c -> (int) c.getSeatsCount()));
        return mapper.toView(venue, seatCounts);
    }
}
