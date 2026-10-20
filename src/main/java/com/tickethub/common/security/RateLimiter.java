package com.tickethub.common.security;

import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.Bucket4jLettuce;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Bucket4j з бакетами в Redis: ліміт спільний для всіх інстансів застосунку.
 * З'єднання створюється ліниво, а збій Redis пропускає запит (fail-open) —
 * rate limiting захищає від ботів, але не повинен зупиняти продаж разом з Redis.
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);
    private static final String KEY_PREFIX = "rate-limit:";

    private final LettuceConnectionFactory connectionFactory;
    private volatile StatefulRedisConnection<String, byte[]> connection;
    private volatile ProxyManager<String> buckets;

    public RateLimiter(LettuceConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public sealed interface Decision {

        record Allowed(long remaining) implements Decision {
        }

        record Rejected(Duration retryAfter) implements Decision {
        }

        /** Redis недоступний — пропускаємо без ліміту. */
        record Unchecked() implements Decision {
        }
    }

    public Decision tryConsume(String key, RateLimitProperties.Limit limit) {
        try {
            var configuration = BucketConfiguration.builder()
                    .addLimit(l -> l.capacity(limit.capacity()).refillGreedy(limit.capacity(), limit.period()))
                    .build();
            var probe = buckets().builder()
                    .build(KEY_PREFIX + key, () -> configuration)
                    .tryConsumeAndReturnRemaining(1);
            if (probe.isConsumed()) {
                return new Decision.Allowed(probe.getRemainingTokens());
            }
            return new Decision.Rejected(Duration.ofNanos(probe.getNanosToWaitForRefill()));
        } catch (RuntimeException e) {
            log.warn("Rate limiter is unavailable, request is let through: {}", e.getMessage());
            return new Decision.Unchecked();
        }
    }

    private ProxyManager<String> buckets() {
        var current = buckets;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (buckets == null) {
                // окреме з'єднання з кодеком String/byte[], але той самий клієнт (хост, пароль, SSL), що й у Spring
                var client = (RedisClient) connectionFactory.getRequiredNativeClient();
                connection = client.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
                connection.setTimeout(Duration.ofMillis(500));
                buckets = Bucket4jLettuce.casBasedBuilder(connection)
                        // бакет, що повністю поповнився, більше не потрібен — Redis видалить його сам
                        .expirationAfterWrite(ExpirationAfterWriteStrategy
                                .basedOnTimeForRefillingBucketUpToMax(Duration.ofSeconds(10)))
                        .build();
            }
            return buckets;
        }
    }

    @PreDestroy
    void close() {
        if (connection != null) {
            connection.closeAsync().orTimeout(1, TimeUnit.SECONDS);
        }
    }
}
