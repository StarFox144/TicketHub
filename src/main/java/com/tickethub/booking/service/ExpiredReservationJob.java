package com.tickethub.booking.service;

import com.tickethub.booking.repository.OrderRepository;
import com.tickethub.event.service.EventSeatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/**
 * Переводить прострочені PENDING-замовлення в EXPIRED і повертає їхні місця в продаж.
 * Кожна порція — окрема коротка транзакція, щоб не тримати блокування на тисячах рядків.
 */
@Component
public class ExpiredReservationJob {

    private static final Logger log = LoggerFactory.getLogger(ExpiredReservationJob.class);

    private final OrderRepository orderRepository;
    private final EventSeatService eventSeatService;
    private final BookingProperties properties;
    private final Clock clock;
    private final TransactionTemplate tx;

    public ExpiredReservationJob(OrderRepository orderRepository, EventSeatService eventSeatService,
                                 BookingProperties properties, Clock clock,
                                 PlatformTransactionManager transactionManager) {
        this.orderRepository = orderRepository;
        this.eventSeatService = eventSeatService;
        this.properties = properties;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${tickethub.booking.expiration-job.interval}",
            initialDelayString = "${tickethub.booking.expiration-job.interval}")
    public void run() {
        int batchSize = properties.expirationJob().batchSize();
        int total = 0;
        int processed;
        do {
            processed = tx.execute(status -> expireBatch(batchSize));
            total += processed;
        } while (processed == batchSize);
        if (total > 0) {
            log.info("Expired {} pending order(s)", total);
        }
    }

    private int expireBatch(int batchSize) {
        var orders = orderRepository.lockExpired(clock.instant(), batchSize);
        orders.forEach(order -> {
            order.expire();
            eventSeatService.release(order.seatIds());
        });
        return orders.size();
    }
}
