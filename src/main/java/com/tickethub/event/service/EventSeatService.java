package com.tickethub.event.service;

import com.tickethub.common.exception.ConflictException;
import com.tickethub.common.exception.NotFoundException;
import com.tickethub.event.domain.EventSeat;
import com.tickethub.event.domain.SeatsNotFoundException;
import com.tickethub.event.domain.SeatsUnavailableException;
import com.tickethub.event.repository.EventRepository;
import com.tickethub.event.repository.EventSeatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Єдина точка, через яку інші модулі змінюють статуси місць.
 * Методи вимагають транзакцію викликача (MANDATORY): резерв місць і створення замовлення
 * мають закомітитися або відкотитися разом.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class EventSeatService {

    private final EventRepository eventRepository;
    private final EventSeatRepository eventSeatRepository;
    private final SeatLockingStrategy locking;

    public EventSeatService(EventRepository eventRepository, EventSeatRepository eventSeatRepository,
                            SeatLockingStrategy locking) {
        this.eventRepository = eventRepository;
        this.eventSeatRepository = eventSeatRepository;
        this.locking = locking;
    }

    /**
     * Все або нічого: якщо хоч одне місце зайняте — 409 зі списком зайнятих, жодне не резервується.
     * now передає викликач, щоб reservedUntil місць і expiresAt замовлення збігалися до мілісекунди.
     */
    public List<ReservedSeat> reserve(UUID eventId, Collection<UUID> seatIds, Instant now, Duration ttl) {
        var event = eventRepository.findById(eventId).orElseThrow(() -> new NotFoundException("Event", eventId));
        if (!event.isOnSale(now)) {
            throw new ConflictException("Event is not on sale");
        }

        List<UUID> sortedIds = seatIds.stream().distinct().sorted().toList();
        List<EventSeat> seats = locking.load(eventId, sortedIds);
        if (seats.size() != sortedIds.size()) {
            var found = new HashSet<>(seats.stream().map(EventSeat::getId).toList());
            throw new SeatsNotFoundException(eventId, sortedIds.stream().filter(id -> !found.contains(id)).toList());
        }

        var unavailable = seats.stream().filter(s -> !s.isAvailable()).map(EventSeat::getId).toList();
        if (!unavailable.isEmpty()) {
            throw new SeatsUnavailableException(unavailable);
        }

        seats.forEach(s -> s.reserve(ttl, now));
        locking.afterReserve(sortedIds);

        return seats.stream()
                .map(s -> new ReservedSeat(s.getId(), s.getPriceZone().getPrice(), s.getPriceZone().getCurrency()))
                .toList();
    }

    /** Звільнити місця скасованого чи простроченого замовлення. */
    public void release(Collection<UUID> seatIds) {
        eventSeatRepository.findAllById(seatIds).forEach(EventSeat::release);
    }
}
