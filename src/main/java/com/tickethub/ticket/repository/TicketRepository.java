package com.tickethub.ticket.repository;

import com.tickethub.ticket.domain.Ticket;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    List<Ticket> findByOrderId(UUID orderId);

    Page<Ticket> findByUserId(UUID userId, Pageable pageable);

    /** Два сканери на різних входах одночасно зчитали той самий QR — пройти має лише один. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Ticket t where t.code = :code")
    Optional<Ticket> findForUpdateByCode(String code);
}
