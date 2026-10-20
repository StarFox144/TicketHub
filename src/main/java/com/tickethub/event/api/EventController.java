package com.tickethub.event.api;

import com.tickethub.event.api.dto.CreateEventRequest;
import com.tickethub.event.api.dto.EventFilter;
import com.tickethub.event.api.dto.EventSummary;
import com.tickethub.event.api.dto.EventView;
import com.tickethub.event.api.dto.PriceZonesRequest;
import com.tickethub.event.api.dto.UpdateEventRequest;
import com.tickethub.event.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
@Tag(name = "Events", description = "Каталог подій і керування ними")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    @Operation(summary = "Каталог опублікованих подій з фільтрами city, from, to, q")
    public Page<EventSummary> catalog(@ParameterObject EventFilter filter,
                                      @ParameterObject @PageableDefault(size = 20, sort = "startsAt") Pageable pageable) {
        return eventService.catalog(filter, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Деталі події та цінові зони")
    public EventView get(@PathVariable UUID id) {
        return eventService.get(id);
    }

    // TODO(етап 2): лише ORGANIZER, організатор — з JWT
    @PostMapping
    @Operation(summary = "Створити подію (DRAFT)")
    public ResponseEntity<EventView> create(@Valid @RequestBody CreateEventRequest request) {
        var view = eventService.create(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(view.id()).toUri();
        return ResponseEntity.created(location).body(view);
    }

    // TODO(етап 2): лише власник події
    @PutMapping("/{id}")
    @Operation(summary = "Редагувати подію")
    public EventView update(@PathVariable UUID id, @Valid @RequestBody UpdateEventRequest request) {
        return eventService.update(id, request);
    }

    // TODO(етап 2): лише власник події
    @PostMapping("/{id}/price-zones")
    @Operation(summary = "Задати цінові зони події")
    public EventView setPriceZones(@PathVariable UUID id, @Valid @RequestBody PriceZonesRequest request) {
        return eventService.setPriceZones(id, request);
    }
}
