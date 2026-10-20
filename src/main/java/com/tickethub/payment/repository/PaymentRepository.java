package com.tickethub.payment.repository;

import com.tickethub.payment.domain.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByOrderId(UUID orderId);

    /** Провайдер може надіслати той самий webhook кілька разів паралельно — обробить лише перший. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.providerPaymentId = :providerPaymentId")
    Optional<Payment> findForUpdateByProviderPaymentId(String providerPaymentId);
}
