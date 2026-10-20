package com.tickethub.booking;

import com.tickethub.event.service.PessimisticSeatLocking;
import com.tickethub.event.service.SeatLockingStrategy;

/**
 * Конфігурація як у проді: Redis-фільтр + песимістичне блокування.
 * Програвші відсіюються ще в Redis, але результат має бути той самий — рівно один переможець.
 */
class RedisClaimsReservationConcurrencyTest extends ReservationConcurrencyTest {

    @Override
    protected Class<? extends SeatLockingStrategy> expectedStrategy() {
        return PessimisticSeatLocking.class;
    }
}
