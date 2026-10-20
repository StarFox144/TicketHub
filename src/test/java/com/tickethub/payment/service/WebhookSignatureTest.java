package com.tickethub.payment.service;

import com.tickethub.common.exception.UnauthorizedException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebhookSignatureTest {

    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");
    private static final String BODY = "{\"providerPaymentId\":\"fake_1\",\"status\":\"SUCCEEDED\"}";

    private final WebhookSignature signature = signatureWith("secret");
    private final long ts = NOW.getEpochSecond();

    @Test
    void validSignature_isAccepted() {
        var header = signature.sign(ts, BODY);

        assertThat(header).startsWith("sha256=").hasSize("sha256=".length() + 64);
        assertThatCode(() -> signature.verify(header, String.valueOf(ts), BODY)).doesNotThrowAnyException();
    }

    @Test
    void tamperedBody_isRejected() {
        var header = signature.sign(ts, BODY);

        assertThatThrownBy(() -> signature.verify(header, String.valueOf(ts), BODY.replace("SUCCEEDED", "FAILED")))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void signatureWithAnotherSecret_isRejected() {
        var forged = signatureWith("guessed").sign(ts, BODY);

        assertThatThrownBy(() -> signature.verify(forged, String.valueOf(ts), BODY))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void replayedOldRequest_isRejected_evenWithValidSignature() {
        long old = NOW.minus(Duration.ofMinutes(6)).getEpochSecond();
        var header = signature.sign(old, BODY);

        assertThatThrownBy(() -> signature.verify(header, String.valueOf(old), BODY))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("window");
    }

    @Test
    void timestampIsPartOfSignature() {
        var header = signature.sign(ts, BODY);

        // зловмисник не може «освіжити» старий запит, підставивши нову мітку часу
        assertThatThrownBy(() -> signature.verify(header, String.valueOf(ts + 1), BODY))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void missingHeaders_areRejected() {
        assertThatThrownBy(() -> signature.verify(null, String.valueOf(ts), BODY))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> signature.verify("sha256=00", "not-a-number", BODY))
                .isInstanceOf(UnauthorizedException.class);
    }

    private static WebhookSignature signatureWith(String secret) {
        return new WebhookSignature(new PaymentProperties(secret, Duration.ofMinutes(5), null),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
