package com.tickethub.event.service;

import com.tickethub.common.exception.BusinessRuleException;
import com.tickethub.common.exception.NotFoundException;
import com.tickethub.common.security.CurrentUser;
import com.tickethub.event.api.dto.CreateEventRequest;
import com.tickethub.event.api.dto.EventFilter;
import com.tickethub.event.api.dto.EventSummary;
import com.tickethub.event.api.dto.EventView;
import com.tickethub.event.api.dto.PriceZonesRequest;
import com.tickethub.event.api.dto.SeatRangeDto;
import com.tickethub.event.api.dto.SeatView;
import com.tickethub.event.api.dto.UpdateEventRequest;
import com.tickethub.event.domain.Event;
import com.tickethub.event.domain.Event.PriceZoneSpec;
import com.tickethub.event.domain.EventStatus;
import com.tickethub.event.domain.SeatRange;
import com.tickethub.event.repository.EventRepository;
import com.tickethub.event.repository.EventSeatRepository;
import com.tickethub.user.domain.Role;
import com.tickethub.user.service.UserService;
import com.tickethub.venue.service.VenueService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    private final EventSeatRepository eventSeatRepository;
    private final SeatMapService seatMapService;
    private final VenueService venueService;
    private final UserService userService;
    private final EventMapper mapper;
    private final Clock clock;

    public EventService(EventRepository eventRepository, EventSeatRepository eventSeatRepository,
                        SeatMapService seatMapService, VenueService venueService, UserService userService,
                        EventMapper mapper, Clock clock) {
        this.eventRepository = eventRepository;
        this.eventSeatRepository = eventSeatRepository;
        this.seatMapService = seatMapService;
        this.venueService = venueService;
        this.userService = userService;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public EventView create(UUID organizerId, CreateEventRequest request) {
        // роль уже перевірена в контролері, але токен міг бути виданий до блокування
        var organizer = userService.getById(organizerId);
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
        var venueSections = venueService.getSectionIds(event.getVenue().getId());
        var specs = request.zones().stream()
                .map(z -> new PriceZoneSpec(z.name(), z.price(), z.currencyOrDefault(), z.seats().stream()
                        .map(dto -> toRange(dto, venueSections))
                        .toList()))
                .toList();
        event.replacePriceZones(specs);
        // flush, щоб нові зони отримали createdAt і порушення обмежень БД вилетіли тут, а не після відповіді
        eventRepository.flush();
        return mapper.toView(event);
    }

    /**
     * Публікація створює event_seats для всіх місць, покритих ціновими зонами.
     * Паралельна повторна публікація впаде на UNIQUE (event_id, seat_id) → 409.
     */
    @Transactional
    public EventView publish(UUID id) {
        var event = getEvent(id);
        var seats = venueService.getSeats(event.getVenue().getId());
        var eventSeats = event.publish(seats, clock.instant());
        eventSeatRepository.saveAll(eventSeats);
        eventSeatRepository.flush();
        return mapper.toView(event);
    }

    /** Для модуля ticket: дані події та підписи місць для PDF і листа. */
    public TicketEventInfo ticketInfo(UUID eventId, Collection<UUID> eventSeatIds) {
        var event = getEvent(eventId);
        var venue = event.getVenue();
        var seats = eventSeatRepository.findSeatViews(eventSeatIds).stream()
                .collect(Collectors.toMap(SeatView::id, Function.identity()));
        return new TicketEventInfo(event.getId(), event.getTitle(), event.getStartsAt(),
                venue.getName(), venue.getAddress(), venue.getCity(), seats);
    }

    /** Карта місць зі статусами. Для чернетки — 404 тим самим правилом, що й деталі події. */
    public List<SeatView> seatMap(UUID id, CurrentUser viewer) {
        get(id, viewer);
        return seatMapService.seatMap(id);
    }

    private SeatRange toRange(SeatRangeDto dto, Set<UUID> venueSections) {
        if (!venueSections.contains(dto.sectionId())) {
            throw new BusinessRuleException("Section %s does not belong to the event venue".formatted(dto.sectionId()));
        }
        return mapper.toDomain(dto);
    }

    /**
     * Чернетку бачать лише її організатор і адміністратор; для решти її не існує (404, а не 403),
     * щоб не розкривати навіть факт наявності неопублікованої події.
     */
    public EventView get(UUID id, CurrentUser viewer) {
        var event = getEvent(id);
        if (event.getStatus() == EventStatus.DRAFT && !canSeeDraft(event, viewer)) {
            throw new NotFoundException("Event", id);
        }
        return mapper.toView(event);
    }

    private static boolean canSeeDraft(Event event, CurrentUser viewer) {
        return viewer != null
                && (viewer.hasRole(Role.ADMIN.name()) || event.getOrganizer().getId().equals(viewer.id()));
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
