package com.tickethub.booking.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Стратегія блокування (tickethub.booking.locking) читається умовами на бінах SeatLockingStrategy. */
@Validated
@ConfigurationProperties("tickethub.booking")
public record BookingProperties(
        @NotNull Duration reservationTtl,
        @Min(1) int maxSeatsPerOrder,
        @Valid @NotNull ExpirationJob expirationJob
) {

    public record ExpirationJob(boolean enabled, @NotNull Duration interval, @Min(1) int batchSize) {
    }
}
