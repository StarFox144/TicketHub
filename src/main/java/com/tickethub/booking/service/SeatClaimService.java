package com.tickethub.booking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Швидкий відсів під час ажіотажу: перед транзакцією в Postgres «застовпити» місця в Redis
 * ({@code SET seat:{id} <token> NX EX <ttl>}). Якщо місце вже застовпив хтось інший — 409 одразу,
 * без з'єднання з БД і без черги на блокуваннях рядків.
 * <p>
 * Redis — лише фільтр, а не джерело правди: успішний claim ще не означає резерв (його вирішує БД),
 * а недоступний Redis не зупиняє продаж — запити просто йдуть у БД, як до етапу 4.
 * Токен — userId + Idempotency-Key (див. {@link #tokenFor}): повтор того самого запиту вважає свої ключі своїми.
 */
@Service
public class SeatClaimService {

    private static final Logger log = LoggerFactory.getLogger(SeatClaimService.class);
    private static final String KEY_PREFIX = "seat:";

    /**
     * Атомарно для всього набору місць (все або нічого). KEYS — ключі місць, ARGV[1] — токен, ARGV[2] — TTL у секундах.
     * Відповідь: {"TAKEN", ключі зайнятих іншими...} або {"CLAIMED", ключі, встановлені саме цим викликом...}.
     */
    private static final RedisScript<List> CLAIM = RedisScript.of("""
            local taken = {}
            for _, key in ipairs(KEYS) do
              local owner = redis.call('GET', key)
              if owner and owner ~= ARGV[1] then taken[#taken + 1] = key end
            end
            if #taken > 0 then
              table.insert(taken, 1, 'TAKEN')
              return taken
            end
            local claimed = {'CLAIMED'}
            for _, key in ipairs(KEYS) do
              if redis.call('SET', key, ARGV[1], 'NX', 'EX', ARGV[2]) then claimed[#claimed + 1] = key end
            end
            return claimed
            """, List.class);

    /** Видаляє лише ключі, що належать токену, — чужий claim того самого місця не зачіпаємо. */
    private static final RedisScript<Long> RELEASE = RedisScript.of("""
            local released = 0
            for _, key in ipairs(KEYS) do
              if redis.call('GET', key) == ARGV[1] then
                redis.call('DEL', key)
                released = released + 1
              end
            end
            return released
            """, Long.class);

    private final StringRedisTemplate redis;
    private final boolean enabled;

    public SeatClaimService(StringRedisTemplate redis, BookingProperties properties) {
        this.redis = redis;
        this.enabled = properties.redisClaims().enabled();
    }

    public sealed interface ClaimResult {

        /** Усі місця наші; newlyClaimed — ті, що встановив саме цей виклик (їх і треба звільнити при невдачі). */
        record Claimed(List<UUID> newlyClaimed) implements ClaimResult {
        }

        record Taken(List<UUID> seatIds) implements ClaimResult {
        }

        /** Redis вимкнено або недоступний — рішення ухвалює лише БД. */
        record Skipped() implements ClaimResult {
        }
    }

    public ClaimResult claim(Collection<UUID> seatIds, String token, Duration ttl) {
        if (!enabled) {
            return new ClaimResult.Skipped();
        }
        try {
            @SuppressWarnings("unchecked")
            List<String> reply = redis.execute(CLAIM, keys(seatIds), token, String.valueOf(ttl.toSeconds()));
            var seats = reply.stream().skip(1).map(SeatClaimService::seatId).toList();
            return "TAKEN".equals(reply.getFirst()) ? new ClaimResult.Taken(seats) : new ClaimResult.Claimed(seats);
        } catch (DataAccessException e) {
            log.warn("Redis is unavailable, seat claims are skipped: {}", e.getMessage());
            return new ClaimResult.Skipped();
        }
    }

    public void release(Collection<UUID> seatIds, String token) {
        if (!enabled || seatIds.isEmpty()) {
            return;
        }
        try {
            redis.execute(RELEASE, keys(seatIds), token);
        } catch (DataAccessException e) {
            // ключі самі зникнуть за TTL; до того місце може хибно отримувати 409 без перевірки БД
            log.warn("Failed to release seat claims, they will expire by TTL: {}", e.getMessage());
        }
    }

    /** Idempotency-Key обирає клієнт, тож двоє користувачів можуть випадково надіслати однаковий — додаємо userId. */
    public static String tokenFor(UUID userId, String idempotencyKey) {
        return userId + ":" + idempotencyKey;
    }

    private static List<String> keys(Collection<UUID> seatIds) {
        return seatIds.stream().distinct().map(id -> KEY_PREFIX + id).toList();
    }

    private static UUID seatId(String key) {
        return UUID.fromString(key.substring(KEY_PREFIX.length()));
    }
}
