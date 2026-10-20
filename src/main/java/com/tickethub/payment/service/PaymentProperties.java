package com.tickethub.payment.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * fakeProviderWebhookUrl не обов'язковий: за замовчуванням фейковий провайдер шле webhook
 * на цей самий застосунок (http://localhost:{порт}/api/v1/payments/webhook).
 */
@Validated
@ConfigurationProperties("tickethub.payment")
public record PaymentProperties(
        @NotBlank String webhookSecret,
        @NotNull Duration webhookTolerance,
        String fakeProviderWebhookUrl
) {
}
