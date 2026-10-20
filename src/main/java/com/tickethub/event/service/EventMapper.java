package com.tickethub.event.service;

import com.tickethub.event.api.dto.EventSummary;
import com.tickethub.event.api.dto.EventView;
import com.tickethub.event.domain.Event;
import com.tickethub.event.domain.PriceZone;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface EventMapper {

    @Mapping(target = "organizerId", source = "organizer.id")
    EventView toView(Event event);

    @Mapping(target = "venueName", source = "venue.name")
    @Mapping(target = "city", source = "venue.city")
    EventSummary toSummary(Event event);

    EventView.PriceZoneView toView(PriceZone zone);
}
