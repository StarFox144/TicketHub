package com.tickethub.event.service;

import com.tickethub.event.domain.EventSeat;
import com.tickethub.event.domain.SeatsUnavailableException;
import com.tickethub.event.repository.EventSeatRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Читаємо без блокувань, а UPDATE містить "where version = ?". Хто закомітив першим — виграв,
 * решта отримує 0 оновлених рядків → OptimisticLockingFailureException → 409.
 * Плюс: ніхто не чекає на блокування. Мінус: під ажіотажем багато марної роботи й відкотів.
 */
@Component
@ConditionalOnProperty(name = "tickethub.booking.locking", havingValue = "optimistic")
public final class OptimisticSeatLocking implements SeatLockingStrategy {

    private final EventSeatRepository repository;

    public OptimisticSeatLocking(EventSeatRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<EventSeat> load(UUID eventId, List<UUID> sortedSeatIds) {
        return repository.findForReservation(eventId, sortedSeatIds);
    }

    @Override
    public void afterReserve(List<UUID> sortedSeatIds) {
        try {
            // flush зараз, щоб конфлікт версій вилетів тут, а не під час коміту поза сервісом
            repository.flush();
        } catch (OptimisticLockingFailureException e) {
            // яке саме місце перехопили, Hibernate не каже — повертаємо весь запитаний набір
            throw new SeatsUnavailableException(sortedSeatIds);
        }
    }
}
