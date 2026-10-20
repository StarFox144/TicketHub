package com.tickethub.booking.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** Ліміт кількості місць — tickethub.booking.max-seats-per-order, перевіряється в OrderService. */
public record CreateOrderRequest(@NotNull UUID eventId, @NotEmpty List<@NotNull UUID> eventSeatIds) {
}
