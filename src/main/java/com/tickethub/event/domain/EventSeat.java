package com.tickethub.event.domain;

import com.tickethub.common.audit.BaseEntity;
import com.tickethub.venue.domain.Seat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Місце на конкретну подію. Фізичне місце (Seat) одне на всі події залу,
 * а статус і ціна в кожної події свої. Переходи статусів живуть тут, а не в сервісах.
 */
@Entity
@Table(name = "event_seats",
        uniqueConstraints = @UniqueConstraint(columnNames = {"event_id", "seat_id"}))
public class EventSeat extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Seat seat;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private PriceZone priceZone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatStatus status = SeatStatus.AVAILABLE;

    private Instant reservedUntil;

    /** Для оптимістичного блокування: Hibernate додає "where version = ?" до UPDATE. */
    @Version
    private long version;

    protected EventSeat() {
    }

    EventSeat(Event event, Seat seat, PriceZone priceZone) {
        this.event = event;
        this.seat = seat;
        this.priceZone = priceZone;
    }

    public void reserve(Duration ttl, Instant now) {
        if (status != SeatStatus.AVAILABLE) {
            throw new SeatsUnavailableException(List.of(getId()));
        }
        status = SeatStatus.RESERVED;
        reservedUntil = now.plus(ttl);
    }

    /** Звільнити резерв (скасування або протухання замовлення). Інші статуси не чіпаємо. */
    public void release() {
        if (status == SeatStatus.RESERVED) {
            status = SeatStatus.AVAILABLE;
            reservedUntil = null;
        }
    }

    public void markSold() {
        if (status != SeatStatus.RESERVED) {
            throw new IllegalStateException("Only reserved seat can be sold, seat %s is %s".formatted(getId(), status));
        }
        status = SeatStatus.SOLD;
        reservedUntil = null;
    }

    /**
     * Продаж без резерву — лише для запізнілої оплати: резерв протух і місце звільнилось,
     * але ніхто інший його не встиг зайняти, тож гроші не повертаємо, а продаємо.
     */
    public void sellAvailable() {
        if (status != SeatStatus.AVAILABLE) {
            throw new SeatsUnavailableException(List.of(getId()));
        }
        status = SeatStatus.SOLD;
    }

    public boolean isAvailable() {
        return status == SeatStatus.AVAILABLE;
    }

    public Event getEvent() {
        return event;
    }

    public Seat getSeat() {
        return seat;
    }

    public PriceZone getPriceZone() {
        return priceZone;
    }

    public SeatStatus getStatus() {
        return status;
    }

    public Instant getReservedUntil() {
        return reservedUntil;
    }

    public long getVersion() {
        return version;
    }
}
