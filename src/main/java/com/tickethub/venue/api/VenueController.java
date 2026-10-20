package com.tickethub.venue.api;

import com.tickethub.venue.api.dto.CreateVenueRequest;
import com.tickethub.venue.api.dto.UpdateVenueRequest;
import com.tickethub.venue.api.dto.VenueSummary;
import com.tickethub.venue.api.dto.VenueView;
import com.tickethub.venue.service.VenueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/venues")
@Tag(name = "Venues", description = "Зали та схеми місць")
public class VenueController {

    private final VenueService venueService;

    public VenueController(VenueService venueService) {
        this.venueService = venueService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Створити зал із секціями та згенерувати місця (ADMIN)")
    public ResponseEntity<VenueView> create(@Valid @RequestBody CreateVenueRequest request) {
        var view = venueService.create(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(view.id()).toUri();
        return ResponseEntity.created(location).body(view);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Оновити назву та адресу залу (ADMIN)")
    public VenueView update(@PathVariable UUID id, @Valid @RequestBody UpdateVenueRequest request) {
        return venueService.update(id, request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Зал зі схемою секцій")
    public VenueView get(@PathVariable UUID id) {
        return venueService.get(id);
    }

    @GetMapping
    @Operation(summary = "Список залів")
    public Page<VenueSummary> list(@RequestParam(required = false) String city,
                                   @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return venueService.list(city, pageable);
    }
}
