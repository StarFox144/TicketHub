package com.tickethub.user.service;

import com.tickethub.common.exception.UnauthorizedException;
import com.tickethub.common.security.JwtProperties;
import com.tickethub.user.domain.RefreshToken;
import com.tickethub.user.domain.User;
import com.tickethub.user.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Ротація refresh-токенів: кожен токен одноразовий. Повторне використання вже відкликаного
 * токена означає, що його вкрали, — тоді відкликаються всі сесії користувача.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final JwtProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshTokenRepository repository, JwtProperties properties, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public String issue(User user) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        repository.save(new RefreshToken(user, hash(rawToken), clock.instant().plus(properties.refreshTokenTtl())));
        return rawToken;
    }

    /**
     * Погашає токен і повертає його власника. noRollbackFor — щоб відкликання всіх сесій
     * при виявленні повторного використання не відкотилося разом з винятком.
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public User consume(String rawToken) {
        var now = clock.instant();
        var token = repository.findForUpdate(hash(rawToken))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        var user = token.getUser();

        if (token.isRevoked()) {
            log.warn("Reuse of revoked refresh token detected for user {}, revoking all sessions", user.getId());
            repository.revokeAllForUser(user.getId(), now);
            throw new UnauthorizedException("Refresh token has been revoked");
        }
        token.revoke(now);
        if (token.isExpired(now)) {
            throw new UnauthorizedException("Refresh token has expired");
        }
        if (!user.isEnabled()) {
            throw new UnauthorizedException("Account is blocked");
        }
        return user;
    }

    @Transactional
    public void revoke(String rawToken) {
        repository.findByTokenHash(hash(rawToken)).ifPresent(t -> t.revoke(clock.instant()));
    }

    @Transactional
    public void revokeAll(UUID userId) {
        repository.revokeAllForUser(userId, clock.instant());
    }

    private static String hash(String rawToken) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
