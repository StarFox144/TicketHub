package com.tickethub.payment.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tickethub.common.security.CurrentUser;
import com.tickethub.payment.api.dto.PaymentView;
import com.tickethub.payment.api.dto.PaymentWebhook;
import com.tickethub.payment.service.PaymentService;
import com.tickethub.payment.service.WebhookSignature;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@Tag(name = "Payments", description = "Оплата замовлень")
public class PaymentController {

    private final PaymentService paymentService;
    private final WebhookSignature signature;
    private final ObjectMapper objectMapper;

    public PaymentController(PaymentService paymentService, WebhookSignature signature, ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.signature = signature;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/api/v1/orders/{id}/pay")
    @PreAuthorize("hasRole('USER')")
    @Operation(summary = "Створити платіж, повертає URL сторінки оплати провайдера")
    public PaymentView pay(@PathVariable UUID id,
                           @Parameter(hidden = true) @AuthenticationPrincipal CurrentUser user) {
        return paymentService.startPayment(id, user.id());
    }

    /**
     * Тіло читається як сирий рядок: підпис рахується саме від байтів, які надіслав провайдер,
     * а не від JSON, повторно серіалізованого після десеріалізації.
     */
    @PostMapping(value = "/api/v1/payments/webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Результат оплати від провайдера (HMAC-підпис у X-Signature, мітка часу в X-Timestamp)")
    public ResponseEntity<Void> webhook(
            @RequestHeader(value = WebhookSignature.SIGNATURE_HEADER, required = false) String signatureHeader,
            @RequestHeader(value = WebhookSignature.TIMESTAMP_HEADER, required = false) String timestampHeader,
            @RequestBody String body) {
        signature.verify(signatureHeader, timestampHeader, body);
        paymentService.handleWebhook(parse(body));
        return ResponseEntity.ok().build();
    }

    private PaymentWebhook parse(String body) {
        try {
            return objectMapper.readValue(body, PaymentWebhook.class);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Malformed webhook payload");
        }
    }
}
