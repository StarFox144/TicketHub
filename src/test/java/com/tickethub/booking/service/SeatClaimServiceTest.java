package com.tickethub.booking.service;

import com.tickethub.booking.service.SeatClaimService.ClaimResult;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SeatClaimServiceTest {

    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

    @Test
    @SuppressWarnings("unchecked")
    void redisDown_claimIsSkipped_soSaleFallsBackToDatabase() {
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("connection refused"));
        var service = new SeatClaimService(redis, properties(true));

        var result = service.claim(List.of(UUID.randomUUID()), "token", TTL);

        assertThat(result).isInstanceOf(ClaimResult.Skipped.class);
        assertThatCode(() -> service.release(List.of(UUID.randomUUID()), "token")).doesNotThrowAnyException();
    }

    @Test
    void disabled_neverTouchesRedis() {
        var service = new SeatClaimService(redis, properties(false));

        assertThat(service.claim(List.of(UUID.randomUUID()), "token", TTL)).isInstanceOf(ClaimResult.Skipped.class);
        verifyNoInteractions(redis);
    }

    private static BookingProperties properties(boolean claimsEnabled) {
        return new BookingProperties(TTL, 10,
                new BookingProperties.ExpirationJob(Duration.ofSeconds(30), 100),
                new BookingProperties.RedisClaims(claimsEnabled));
    }
}
