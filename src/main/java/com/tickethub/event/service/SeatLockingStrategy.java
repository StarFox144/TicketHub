package com.tickethub.event.service;

import com.tickethub.event.domain.EventSeat;

import java.util.List;
import java.util.UUID;

/**
 * Два способи не продати місце двічі; перемикаються властивістю tickethub.booking.locking.
 * Порівняння — у ReservationConcurrencyTest (обидва проходять тест на 50 потоків).
 */
public sealed interface SeatLockingStrategy permits PessimisticSeatLocking, OptimisticSeatLocking {

    /** Завантажити місця для резервування (впорядковані за id). */
    List<EventSeat> load(UUID eventId, List<UUID> sortedSeatIds);

    /** Викликається після зміни статусів; оптимістичний варіант тут виявляє конфлікт версій. */
    void afterReserve(List<UUID> sortedSeatIds);
}
