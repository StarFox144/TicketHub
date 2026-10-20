package com.tickethub.event.domain;

import com.tickethub.common.exception.NotFoundException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SeatsNotFoundException extends NotFoundException {

    private final List<UUID> missingSeatIds;

    public SeatsNotFoundException(UUID eventId, List<UUID> missingSeatIds) {
        super("Seats of event", eventId);
        this.missingSeatIds = List.copyOf(missingSeatIds);
    }

    @Override
    public Map<String, Object> properties() {
        return Map.of("missingSeatIds", missingSeatIds);
    }
}
