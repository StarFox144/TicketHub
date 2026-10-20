package com.tickethub.payment.service;

import com.tickethub.common.exception.UnauthorizedException;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * HMAC-SHA256 підпис webhook-ів (схема як у Stripe): підписується "{timestamp}.{сире тіло}".
 * <ul>
 *   <li>підпис — запит справді від провайдера і тіло не змінено;</li>
 *   <li>мітка часу в підписі — перехоплений запит не можна відтворити пізніше за вікно tolerance;</li>
 *   <li>порівняння за сталий час — підпис не підбирається по одному байту за часом відповіді.</li>
 * </ul>
 */
@Component
public class WebhookSignature {

    public static final String SIGNATURE_HEADER = "X-Signature";
    public static final String TIMESTAMP_HEADER = "X-Timestamp";
    private static final String SCHEME = "sha256=";

    private final SecretKeySpec key;
    private final Duration tolerance;
    private final Clock clock;

    public WebhookSignature(PaymentProperties properties, Clock clock) {
        this.key = new SecretKeySpec(properties.webhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.tolerance = properties.webhookTolerance();
        this.clock = clock;
    }

    public String sign(long epochSeconds, String body) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            var digest = mac.doFinal((epochSeconds + "." + body).getBytes(StandardCharsets.UTF_8));
            return SCHEME + HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }

    public void verify(String signature, String timestamp, String body) {
        if (signature == null || timestamp == null) {
            throw new UnauthorizedException("Webhook signature is missing");
        }
        long epochSeconds;
        try {
            epochSeconds = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            throw new UnauthorizedException("Webhook timestamp is malformed");
        }
        var age = Duration.between(Instant.ofEpochSecond(epochSeconds), clock.instant()).abs();
        if (age.compareTo(tolerance) > 0) {
            throw new UnauthorizedException("Webhook timestamp is outside the allowed window");
        }
        var expected = sign(epochSeconds, body).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.UTF_8))) {
            throw new UnauthorizedException("Webhook signature is invalid");
        }
    }
}
