package com.tickethub.ticket.domain;

import com.tickethub.common.audit.BaseEntity;
import com.tickethub.common.exception.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Квиток на одне місце (1:1 з OrderItem). code — те, що зашито в QR: 192 випадкові біти,
 * тож код неможливо вгадати чи підібрати, а з самого коду нічого не можна дізнатися.
 */
@Entity
@Table(name = "tickets")
public class Ticket extends BaseEntity {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Column(nullable = false, unique = true, updatable = false)
    private UUID orderItemId;

    @Column(nullable = false, updatable = false)
    private UUID orderId;

    @Column(nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private UUID eventId;

    @Column(nullable = false, updatable = false)
    private UUID eventSeatId;

    @Column(nullable = false, unique = true, updatable = false, length = 64)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status = TicketStatus.VALID;

    private String pdfPath;

    private Instant usedAt;

    protected Ticket() {
    }

    public Ticket(UUID orderItemId, UUID orderId, UUID userId, UUID eventId, UUID eventSeatId) {
        this.orderItemId = orderItemId;
        this.orderId = orderId;
        this.userId = userId;
        this.eventId = eventId;
        this.eventSeatId = eventSeatId;
        this.code = newCode();
    }

    /** Пропуск на вході. Повторне сканування того самого квитка — 409 із часом першого проходу. */
    public void use(Instant now) {
        switch (status) {
            case VALID -> {
                status = TicketStatus.USED;
                usedAt = now;
            }
            case USED -> throw new TicketAlreadyUsedException(usedAt);
            case CANCELLED -> throw new ConflictException("Ticket has been cancelled");
        }
    }

    public void attachPdf(String pdfPath) {
        this.pdfPath = pdfPath;
    }

    private static String newCode() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public UUID getOrderItemId() {
        return orderItemId;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getEventId() {
        return eventId;
    }

    public UUID getEventSeatId() {
        return eventSeatId;
    }

    public String getCode() {
        return code;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public String getPdfPath() {
        return pdfPath;
    }

    public Instant getUsedAt() {
        return usedAt;
    }
}
