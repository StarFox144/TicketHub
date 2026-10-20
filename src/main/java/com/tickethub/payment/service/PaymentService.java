package com.tickethub.payment.service;

import com.tickethub.booking.service.OrderPayments.PaymentConfirmation;
import com.tickethub.booking.service.OrderService;
import com.tickethub.common.exception.BusinessRuleException;
import com.tickethub.common.exception.ConflictException;
import com.tickethub.common.exception.NotFoundException;
import com.tickethub.payment.api.dto.PaymentView;
import com.tickethub.payment.api.dto.PaymentWebhook;
import com.tickethub.payment.domain.Payment;
import com.tickethub.payment.domain.PaymentStatus;
import com.tickethub.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final OrderService orderService;
    private final FakePaymentProvider provider;
    private final Clock clock;

    public PaymentService(PaymentRepository paymentRepository, OrderService orderService,
                          FakePaymentProvider provider, Clock clock) {
        this.paymentRepository = paymentRepository;
        this.orderService = orderService;
        this.provider = provider;
        this.clock = clock;
    }

    /** Повторний виклик повертає ту саму оплату; після невдалої спроби — нову спробу в провайдера. */
    @Transactional
    public PaymentView startPayment(UUID orderId, UUID userId) {
        var order = orderService.getPayableOrder(orderId, userId);
        var payment = paymentRepository.findByOrderId(orderId).orElse(null);
        if (payment == null) {
            payment = paymentRepository.save(
                    new Payment(orderId, provider.newPaymentId(), order.amount(), order.currency()));
        } else if (payment.getStatus() == PaymentStatus.FAILED) {
            payment.restart(provider.newPaymentId());
        } else if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            throw new ConflictException("Order has already been paid");
        }
        var checkoutUrl = provider.checkoutUrl(payment.getProviderPaymentId(), payment.getAmount(), payment.getCurrency());
        return new PaymentView(payment.getId(), orderId, payment.getStatus(), payment.getAmount(),
                payment.getCurrency(), checkoutUrl);
    }

    /**
     * Результат оплати від провайдера. В одній транзакції: Payment → SUCCEEDED, Order → PAID,
     * місця → SOLD, подія order.paid → outbox. Ідемпотентно: повторний webhook нічого не змінює.
     */
    @Transactional
    public void handleWebhook(PaymentWebhook webhook) {
        var payment = paymentRepository.findForUpdateByProviderPaymentId(webhook.providerPaymentId())
                .orElseThrow(() -> new NotFoundException("Payment " + webhook.providerPaymentId() + " not found"));
        if (!payment.isAwaitingResult()) {
            log.info("Duplicate webhook for payment {} ignored", payment.getId());
            return;
        }
        if (!webhook.succeeded()) {
            // замовлення лишається PENDING: покупець може спробувати ще раз, поки не скінчився резерв
            payment.fail();
            return;
        }
        if (webhook.amount() == null || payment.getAmount().compareTo(webhook.amount()) != 0
                || !payment.getCurrency().equals(webhook.currency())) {
            log.error("Webhook amount {} {} does not match payment {} ({} {})", webhook.amount(), webhook.currency(),
                    payment.getId(), payment.getAmount(), payment.getCurrency());
            throw new BusinessRuleException("Paid amount does not match the order");
        }

        payment.succeed(webhook.occurredAt() != null ? webhook.occurredAt() : clock.instant());
        if (orderService.confirmPayment(payment.getOrderId()) == PaymentConfirmation.REFUND_REQUIRED) {
            payment.markRefunded(clock.instant());
            refundAfterCommit(payment.getProviderPaymentId());
        }
    }

    /** Виклик зовнішньої системи — лише після коміту: інакше відкат транзакції лишив би гроші поверненими «насправді». */
    private void refundAfterCommit(String providerPaymentId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    provider.refund(providerPaymentId);
                } catch (RuntimeException e) {
                    log.error("Refund of payment {} failed and must be retried manually", providerPaymentId, e);
                }
            }
        });
    }
}
