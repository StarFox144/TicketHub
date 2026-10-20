package com.tickethub.booking;

import com.tickethub.event.service.PessimisticSeatLocking;
import com.tickethub.event.service.SeatLockingStrategy;
import org.springframework.test.context.TestPropertySource;

/**
 * SELECT ... FOR UPDATE — стратегія за замовчуванням. Redis-фільтр вимкнено,
 * щоб усі 50 запитів дійшли до Postgres і тест перевіряв саме блокування рядків.
 */
@TestPropertySource(properties = "tickethub.booking.redis-claims.enabled=false")
class PessimisticReservationConcurrencyTest extends ReservationConcurrencyTest {

    @Override
    protected Class<? extends SeatLockingStrategy> expectedStrategy() {
        return PessimisticSeatLocking.class;
    }
}
