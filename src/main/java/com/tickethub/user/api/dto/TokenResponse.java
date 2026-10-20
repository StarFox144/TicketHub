package com.tickethub.user.api.dto;

import java.time.Instant;

public record TokenResponse(String tokenType, String accessToken, Instant accessTokenExpiresAt, String refreshToken) {

    public static TokenResponse bearer(String accessToken, Instant accessTokenExpiresAt, String refreshToken) {
        return new TokenResponse("Bearer", accessToken, accessTokenExpiresAt, refreshToken);
    }
}
