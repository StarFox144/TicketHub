package com.tickethub.event.domain;

import com.tickethub.common.exception.ConflictException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 409 зі списком місць, які вже зайняті, — клієнт може одразу підсвітити їх на карті залу. */
public class SeatsUnavailableException extends ConflictException {

    private final List<UUID> unavailableSeatIds;

    public SeatsUnavailableException(List<UUID> unavailableSeatIds) {
        super("Some of the requested seats are no longer available");
        this.unavailableSeatIds = List.copyOf(unavailableSeatIds);
    }

    public List<UUID> getUnavailableSeatIds() {
        return unavailableSeatIds;
    }

    @Override
    public Map<String, Object> properties() {
        return Map.of("unavailableSeatIds", unavailableSeatIds);
    }
}
