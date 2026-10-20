package com.tickethub.booking;

import com.tickethub.event.service.OptimisticSeatLocking;
import com.tickethub.event.service.SeatLockingStrategy;
import org.springframework.test.context.TestPropertySource;

/** @Version — окремий Spring-контекст з іншою стратегією, той самий контейнер Postgres. */
@TestPropertySource(properties = "tickethub.booking.locking=optimistic")
class OptimisticReservationConcurrencyTest extends ReservationConcurrencyTest {

    @Override
    protected Class<? extends SeatLockingStrategy> expectedStrategy() {
        return OptimisticSeatLocking.class;
    }
}
