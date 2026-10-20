package com.tickethub.booking;

import com.tickethub.booking.api.dto.CreateOrderRequest;
import com.tickethub.booking.service.OrderService;
import com.tickethub.event.domain.SeatsUnavailableException;
import com.tickethub.event.service.SeatLockingStrategy;
import com.tickethub.support.IntegrationTestBase;
import com.tickethub.user.domain.Role;
import com.tickethub.user.domain.User;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Головний тест проєкту: багато потоків одночасно купують одне й те саме місце на реальному Postgres.
 * Запускається для обох стратегій блокування (див. підкласи) — обидві мають дати рівно одного переможця.
 */
abstract class ReservationConcurrencyTest extends IntegrationTestBase {

    private static final Logger log = LoggerFactory.getLogger(ReservationConcurrencyTest.class);

    @Autowired
    private OrderService orderService;

    @Autowired
    private SeatLockingStrategy locking;

    @Autowired
    private JdbcTemplate jdbc;

    protected abstract Class<? extends SeatLockingStrategy> expectedStrategy();

    @Test
    void fiftyBuyers_sameSeat_exactlyOneWins() throws Exception {
        assertThat(locking).isInstanceOf(expectedStrategy());
        var event = testData.publishedEvent();
        var seat = event.seat(0);

        var outcome = runConcurrently(50, buyer -> new CreateOrderRequest(event.eventId(), List.of(seat)));

        log.info("[{}] 50 buyers / 1 seat: {} success, {} conflicts, {} ms",
                locking.getClass().getSimpleName(), outcome.successes(), outcome.conflicts(), outcome.millis());
        assertThat(outcome.unexpected()).isEmpty();
        assertThat(outcome.successes()).isEqualTo(1);
        assertThat(outcome.conflicts()).isEqualTo(49);

        assertThat(jdbc.queryForObject("select count(*) from orders where event_id = ? and status = 'PENDING'",
                Integer.class, event.eventId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from order_items where event_seat_id = ? and active",
                Integer.class, seat)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from event_seats where id = ?", String.class, seat))
                .isEqualTo("RESERVED");
    }

    /**
     * Перехресні набори місць у різному порядку: без сортування блокувань за id тут виникав би deadlock.
     * Переможець один, усі інші — чисті 409, жодної помилки від БД.
     */
    @Test
    void overlappingSeatSets_inRandomOrder_noDeadlocks() throws Exception {
        var event = testData.publishedEvent();
        var seats = List.of(event.seat(0), event.seat(1), event.seat(2));

        var outcome = runConcurrently(30, buyer -> {
            var shuffled = new ArrayList<>(seats);
            Collections.shuffle(shuffled);
            return new CreateOrderRequest(event.eventId(), shuffled);
        });

        log.info("[{}] 30 buyers / 3 overlapping seats: {} success, {} conflicts, {} ms",
                locking.getClass().getSimpleName(), outcome.successes(), outcome.conflicts(), outcome.millis());
        assertThat(outcome.unexpected()).isEmpty();
        assertThat(outcome.successes()).isEqualTo(1);
        assertThat(outcome.conflicts()).isEqualTo(29);
        assertThat(jdbc.queryForObject("select count(*) from event_seats where event_id = ? and status = 'RESERVED'",
                Integer.class, event.eventId())).isEqualTo(3);
    }

    /** Різні покупці — різні місця: ніхто нікому не заважає. */
    @Test
    void buyersOfDifferentSeats_allSucceed() throws Exception {
        var event = testData.publishedEvent();
        var next = new AtomicInteger();

        var outcome = runConcurrently(10,
                buyer -> new CreateOrderRequest(event.eventId(), List.of(event.seat(next.getAndIncrement()))));

        assertThat(outcome.unexpected()).isEmpty();
        assertThat(outcome.successes()).isEqualTo(10);
    }

    private record Outcome(int successes, int conflicts, List<Throwable> unexpected, long millis) {
    }

    private Outcome runConcurrently(int buyers, Function<User, CreateOrderRequest> requestFor) throws Exception {
        List<User> users = IntStream.range(0, buyers).mapToObj(i -> testData.user(Role.USER)).toList();
        var ready = new CountDownLatch(buyers);
        var start = new CountDownLatch(1);
        var successes = new AtomicInteger();
        var conflicts = new AtomicInteger();
        Queue<Throwable> unexpected = new ConcurrentLinkedQueue<>();

        try (var executor = Executors.newFixedThreadPool(buyers)) {
            for (var user : users) {
                var request = requestFor.apply(user);
                executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        orderService.placeOrder(user.getId(), request, UUID.randomUUID().toString());
                        successes.incrementAndGet();
                    } catch (SeatsUnavailableException e) {
                        conflicts.incrementAndGet();
                    } catch (Throwable e) {
                        unexpected.add(e);
                    }
                });
            }
            ready.await();
            long startedAt = System.nanoTime();
            start.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(60, TimeUnit.SECONDS)).as("all buyers finished").isTrue();
            long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            unexpected.forEach(e -> log.error("Unexpected failure", e));
            return new Outcome(successes.get(), conflicts.get(), List.copyOf(unexpected), millis);
        }
    }
}
