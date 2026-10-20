package com.tickethub.payment.domain;

import com.tickethub.common.audit.BaseEntity;
import com.tickethub.common.exception.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Одна оплата на замовлення; після невдалої спроби та сама оплата перезапускається з новим id провайдера. */
@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

    @Column(nullable = false, unique = true, updatable = false)
    private UUID orderId;

    @Column(nullable = false, unique = true)
    private String providerPaymentId;

    @Column(nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.CREATED;

    private Instant paidAt;

    private Instant refundedAt;

    protected Payment() {
    }

    public Payment(UUID orderId, String providerPaymentId, BigDecimal amount, String currency) {
        this.orderId = orderId;
        this.providerPaymentId = providerPaymentId;
        this.amount = amount;
        this.currency = currency;
    }

    public void restart(String newProviderPaymentId) {
        if (status != PaymentStatus.FAILED) {
            throw new ConflictException("Only failed payment can be restarted, current status is " + status);
        }
        providerPaymentId = newProviderPaymentId;
        status = PaymentStatus.CREATED;
    }

    public void succeed(Instant at) {
        requireAwaitingResult();
        status = PaymentStatus.SUCCEEDED;
        paidAt = at;
    }

    public void fail() {
        requireAwaitingResult();
        status = PaymentStatus.FAILED;
    }

    public void markRefunded(Instant at) {
        if (status != PaymentStatus.SUCCEEDED) {
            throw new IllegalStateException("Only succeeded payment can be refunded, payment " + getId() + " is " + status);
        }
        refundedAt = at;
    }

    /** Ще не отримали результат від провайдера; повторний webhook для завершеної оплати ігнорується. */
    public boolean isAwaitingResult() {
        return status == PaymentStatus.CREATED;
    }

    private void requireAwaitingResult() {
        if (!isAwaitingResult()) {
            throw new IllegalStateException("Payment %s has already been completed with %s".formatted(getId(), status));
        }
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getProviderPaymentId() {
        return providerPaymentId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getRefundedAt() {
        return refundedAt;
    }
}
