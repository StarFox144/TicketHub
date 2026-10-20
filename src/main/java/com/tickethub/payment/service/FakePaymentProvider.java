package com.tickethub.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tickethub.common.exception.ConflictException;
import com.tickethub.payment.api.dto.PaymentWebhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Імітація зовнішнього платіжного провайдера (у MVP справжньої платіжки немає).
 * Як і справжній: видає сторінку оплати, а результат повідомляє асинхронно — підписаним
 * HTTP-запитом на наш webhook. Стан зберігається в пам'яті й зникає з перезапуском.
 */
@Component
public class FakePaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(FakePaymentProvider.class);
    private static final String WEBHOOK_PATH = "/api/v1/payments/webhook";

    public enum State { PENDING, SUCCEEDED, FAILED, REFUNDED }

    public record ProviderPayment(String id, BigDecimal amount, String currency, State state) {

        ProviderPayment withState(State newState) {
            return new ProviderPayment(id, amount, currency, newState);
        }
    }

    public record WebhookDelivery(URI url, int status) {
    }

    private final Map<String, ProviderPayment> payments = new ConcurrentHashMap<>();
    private final WebhookSignature signature;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final Environment environment;
    private final PaymentProperties properties;
    private final Clock clock;

    public FakePaymentProvider(WebhookSignature signature, ObjectMapper objectMapper, RestClient.Builder restClient,
                               Environment environment, PaymentProperties properties, Clock clock) {
        this.signature = signature;
        this.objectMapper = objectMapper;
        this.restClient = restClient.build();
        this.environment = environment;
        this.properties = properties;
        this.clock = clock;
    }

    public String newPaymentId() {
        return "fake_" + UUID.randomUUID().toString().replace("-", "");
    }

    /** Реєструє оплату в «провайдера» (ідемпотентно) і повертає URL сторінки оплати. */
    public URI checkoutUrl(String providerPaymentId, BigDecimal amount, String currency) {
        payments.putIfAbsent(providerPaymentId, new ProviderPayment(providerPaymentId, amount, currency, State.PENDING));
        // у межах HTTP-запиту — адреса, якою прийшов клієнт; поза ним (job, тест) — localhost
        var base = RequestContextHolder.getRequestAttributes() != null
                ? ServletUriComponentsBuilder.fromCurrentContextPath()
                : UriComponentsBuilder.fromUriString("http://localhost:" + localPort());
        return base.path("/fake-provider/checkout/{id}").buildAndExpand(providerPaymentId).toUri();
    }

    public Optional<ProviderPayment> find(String providerPaymentId) {
        return Optional.ofNullable(payments.get(providerPaymentId));
    }

    /** Покупець натиснув «Оплатити» чи «Відхилити» — провайдер повідомляє магазин через webhook. */
    public WebhookDelivery complete(String providerPaymentId, boolean success) {
        var payment = find(providerPaymentId)
                .orElseThrow(() -> new ConflictException("Unknown payment " + providerPaymentId
                        + " (the fake provider keeps payments in memory and forgets them on restart)"));
        if (payment.state() != State.PENDING) {
            throw new ConflictException("Payment has already been completed: " + payment.state());
        }
        payments.put(providerPaymentId, payment.withState(success ? State.SUCCEEDED : State.FAILED));

        var webhook = new PaymentWebhook(providerPaymentId, success ? "SUCCEEDED" : "FAILED",
                payment.amount(), payment.currency(), clock.instant());
        return deliver(webhook);
    }

    public void refund(String providerPaymentId) {
        payments.computeIfPresent(providerPaymentId, (id, payment) -> payment.withState(State.REFUNDED));
        log.info("Fake provider refunded payment {}", providerPaymentId);
    }

    private WebhookDelivery deliver(PaymentWebhook webhook) {
        var url = webhookUrl();
        try {
            var body = objectMapper.writeValueAsString(webhook);
            long timestamp = clock.instant().getEpochSecond();
            var response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(WebhookSignature.TIMESTAMP_HEADER, String.valueOf(timestamp))
                    .header(WebhookSignature.SIGNATURE_HEADER, signature.sign(timestamp, body))
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            return new WebhookDelivery(url, response.getStatusCode().value());
        } catch (RestClientResponseException e) {
            log.warn("Webhook {} was rejected with {}", url, e.getStatusCode());
            return new WebhookDelivery(url, e.getStatusCode().value());
        } catch (JsonProcessingException | RestClientException e) {
            throw new IllegalStateException("Failed to deliver webhook to " + url, e);
        }
    }

    private URI webhookUrl() {
        if (properties.fakeProviderWebhookUrl() != null) {
            return URI.create(properties.fakeProviderWebhookUrl());
        }
        return URI.create("http://localhost:" + localPort() + WEBHOOK_PATH);
    }

    /** local.server.port — фактичний порт (у тестах із RANDOM_PORT він відомий лише після старту). */
    private String localPort() {
        return environment.getProperty("local.server.port", environment.getProperty("server.port", "8080"));
    }
}
