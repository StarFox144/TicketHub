package com.tickethub.common.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Застосунок не стартує без секрету — краще впасти одразу, ніж підписувати токени порожнім ключем. */
@Validated
@ConfigurationProperties("tickethub.security.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl
) {
}
