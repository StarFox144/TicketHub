package com.tickethub.common.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");
    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[64]);
    private static final JwtProperties PROPERTIES =
            new JwtProperties(SECRET, Duration.ofMinutes(15), Duration.ofDays(7));

    private final CurrentUser user = new CurrentUser(UUID.randomUUID(), "user@test.local", "ORGANIZER");

    @Test
    void issuedToken_parsesBackToSameUser() {
        var service = serviceAt(NOW);

        var token = service.issueAccessToken(user);

        assertThat(token.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        assertThat(service.parse(token.value())).contains(user);
    }

    @Test
    void expiredToken_isRejected() {
        var token = serviceAt(NOW).issueAccessToken(user).value();

        assertThat(serviceAt(NOW.plus(Duration.ofMinutes(16))).parse(token)).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherKey_isRejected() {
        var otherSecret = Base64.getEncoder().encodeToString(new byte[64]).replace('A', 'B');
        var foreign = new JwtService(new JwtProperties(otherSecret, Duration.ofMinutes(15), Duration.ofDays(7)),
                Clock.fixed(NOW, ZoneOffset.UTC));

        var token = foreign.issueAccessToken(user).value();

        assertThat(serviceAt(NOW).parse(token)).isEmpty();
    }

    @Test
    void garbage_isRejected() {
        assertThat(serviceAt(NOW).parse("not.a.jwt")).isEmpty();
        assertThat(serviceAt(NOW).parse("")).isEmpty();
    }

    private static JwtService serviceAt(Instant instant) {
        return new JwtService(PROPERTIES, Clock.fixed(instant, ZoneOffset.UTC));
    }
}
