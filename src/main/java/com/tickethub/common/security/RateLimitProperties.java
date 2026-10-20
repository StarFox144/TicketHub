package com.tickethub.common.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** capacity запитів за period; поповнюється рівномірно (refillGreedy), а не одним стрибком наприкінці вікна. */
@Validated
@ConfigurationProperties("tickethub.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        @Valid @NotNull Limit login,
        @Valid @NotNull Limit orders
) {

    public record Limit(@Min(1) long capacity, @NotNull Duration period) {
    }
}
