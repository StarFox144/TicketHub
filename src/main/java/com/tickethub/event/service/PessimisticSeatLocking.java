package com.tickethub.event.service;

import com.tickethub.event.domain.EventSeat;
import com.tickethub.event.repository.EventSeatRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Конкуренти чекають на блокування рядка і після коміту переможця бачать статус RESERVED.
 * Плюс: передбачувано, без повторів. Мінус: під ажіотажем потоки стоять у черзі на одних і тих самих рядках.
 */
@Component
@ConditionalOnProperty(name = "tickethub.booking.locking", havingValue = "pessimistic", matchIfMissing = true)
public final class PessimisticSeatLocking implements SeatLockingStrategy {

    private final EventSeatRepository repository;

    public PessimisticSeatLocking(EventSeatRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<EventSeat> load(UUID eventId, List<UUID> sortedSeatIds) {
        return repository.lockForReservation(eventId, sortedSeatIds);
    }

    @Override
    public void afterReserve(List<UUID> sortedSeatIds) {
        // рядки вже заблоковані, конфлікту при UPDATE бути не може
    }
}
