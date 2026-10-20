package com.tickethub.booking;

import com.tickethub.event.service.PessimisticSeatLocking;
import com.tickethub.event.service.SeatLockingStrategy;

/** SELECT ... FOR UPDATE — стратегія за замовчуванням. */
class PessimisticReservationConcurrencyTest extends ReservationConcurrencyTest {

    @Override
    protected Class<? extends SeatLockingStrategy> expectedStrategy() {
        return PessimisticSeatLocking.class;
    }
}
