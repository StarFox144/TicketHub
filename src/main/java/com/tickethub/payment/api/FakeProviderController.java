package com.tickethub.payment.api;

import com.tickethub.common.exception.NotFoundException;
import com.tickethub.payment.service.FakePaymentProvider;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/**
 * Сторінка оплати фейкового провайдера — щоб пройти весь сценарій у браузері.
 * У справжній інтеграції цього контролера немає: сторінка живе на боці платіжної системи.
 */
@Hidden
@RestController
@RequestMapping("/fake-provider/checkout")
public class FakeProviderController {

    private final FakePaymentProvider provider;

    public FakeProviderController(FakePaymentProvider provider) {
        this.provider = provider;
    }

    @GetMapping(value = "/{providerPaymentId}", produces = MediaType.TEXT_HTML_VALUE)
    public String checkout(@PathVariable String providerPaymentId) {
        var payment = provider.find(providerPaymentId)
                .orElseThrow(() -> new NotFoundException("Payment " + providerPaymentId + " not found"));
        var id = HtmlUtils.htmlEscape(providerPaymentId);
        return """
                <!doctype html>
                <html lang="uk"><head><meta charset="utf-8"><title>Fake Pay</title></head>
                <body style="font-family: sans-serif; max-width: 420px; margin: 48px auto">
                  <h2>Fake Pay</h2>
                  <p>Платіж <code>%s</code></p>
                  <p style="font-size: 24px"><b>%s %s</b></p>
                  <p>Стан: %s</p>
                  <form method="post" action="%s/complete?outcome=success"><button>Оплатити</button></form>
                  <form method="post" action="%s/complete?outcome=failure"><button>Відхилити</button></form>
                </body></html>
                """.formatted(id, payment.amount(), payment.currency(), payment.state(), id, id);
    }

    /** Імітує натискання кнопки покупцем: провайдер шле підписаний webhook магазину. */
    @PostMapping(value = "/{providerPaymentId}/complete", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> complete(@PathVariable String providerPaymentId,
                                           @RequestParam String outcome) {
        var delivery = provider.complete(providerPaymentId, "success".equals(outcome));
        var html = """
                <!doctype html>
                <html lang="uk"><head><meta charset="utf-8"><title>Fake Pay</title></head>
                <body style="font-family: sans-serif; max-width: 420px; margin: 48px auto">
                  <h2>%s</h2>
                  <p>Webhook → %s: HTTP %d</p>
                </body></html>
                """.formatted("success".equals(outcome) ? "Оплату проведено" : "Оплату відхилено",
                HtmlUtils.htmlEscape(delivery.url().toString()), delivery.status());
        return ResponseEntity.ok(html);
    }
}
