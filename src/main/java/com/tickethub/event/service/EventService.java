package com.tickethub.event.service;

import com.tickethub.common.exception.BusinessRuleException;
import com.tickethub.common.exception.NotFoundException;
import com.tickethub.event.api.dto.CreateEventRequest;
import com.tickethub.event.api.dto.EventFilter;
import com.tickethub.event.api.dto.EventSummary;
import com.tickethub.event.api.dto.EventView;
import com.tickethub.event.api.dto.PriceZonesRequest;
import com.tickethub.event.api.dto.UpdateEventRequest;
import com.tickethub.event.domain.Event;
import com.tickethub.event.domain.Event.PriceZoneSpec;
import com.tickethub.event.domain.EventStatus;
import com.tickethub.event.repository.EventRepository;
import com.tickethub.user.service.UserService;
import com.tickethub.venue.service.VenueService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import static com.tickethub.event.repository.EventSpecifications.hasStatus;
import static com.tickethub.event.repository.EventSpecifications.inCity;
import static com.tickethub.event.repository.EventSpecifications.startsBefore;
import static com.tickethub.event.repository.EventSpecifications.startsFrom;
import static com.tickethub.event.repository.EventSpecifications.titleContains;

@Service
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;
    private final VenueService venueService;
    private final UserService userService;
    private final EventMapper mapper;
    private final Clock clock;

    public EventService(EventRepository eventRepository, VenueService venueService, UserService userService,
                        EventMapper mapper, Clock clock) {
        this.eventRepository = eventRepository;
        this.venueService = venueService;
        this.userService = userService;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public EventView create(CreateEventRequest request) {
        var organizer = userService.getById(request.organizerId());
        if (!organizer.canOrganize()) {
            throw new BusinessRuleException("User %s cannot organize events".formatted(organizer.getId()));
        }
        var venue = venueService.getVenue(request.venueId());
        var event = new Event(venue, organizer, request.title(), request.description(),
                request.startsAt(), request.endsAt(), clock.instant());
        return mapper.toView(eventRepository.save(event));
    }

    @Transactional
    public EventView update(UUID id, UpdateEventRequest request) {
        var event = getEvent(id);
        event.update(request.title(), request.description(), request.startsAt(), request.endsAt(), clock.instant());
        return mapper.toView(event);
    }

    @Transactional
    public EventView setPriceZones(UUID id, PriceZonesRequest request) {
        var event = getEvent(id);
        var specs = request.zones().stream()
                .map(z -> new PriceZoneSpec(z.name(), z.price(), z.currencyOrDefault()))
                .toList();
        event.replacePriceZones(specs);
        // flush, щоб нові зони отримали createdAt і порушення обмежень БД вилетіли тут, а не після відповіді
        eventRepository.flush();
        return mapper.toView(event);
    }

    public EventView get(UUID id) {
        return mapper.toView(getEvent(id));
    }

    /** Публічний каталог: лише опубліковані події. */
    public Page<EventSummary> catalog(EventFilter filter, Pageable pageable) {
        var spec = Specification.allOf(Stream.of(
                        hasStatus(EventStatus.PUBLISHED),
                        inCity(filter.city()),
                        startsFrom(filter.from()),
                        startsBefore(filter.to()),
                        titleContains(filter.q()))
                .filter(Objects::nonNull)
                .toList());
        return eventRepository.findAll(spec, pageable).map(mapper::toSummary);
    }

    private Event getEvent(UUID id) {
        return eventRepository.findDetailedById(id).orElseThrow(() -> new NotFoundException("Event", id));
    }
}
