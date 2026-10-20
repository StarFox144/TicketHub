package com.tickethub.common.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;

/**
 * Доставляє події з outbox у RabbitMQ. Подія позначається надісланою лише після publisher confirm
 * від брокера; якщо брокер недоступний — транзакція відкочується, і наступний запуск спробує знову.
 * Гарантія — at-least-once: споживачі мають бути ідемпотентними (messageId = id події в outbox).
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final long CONFIRM_TIMEOUT_MS = 5_000;

    private final OutboxRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final String exchange;
    private final int batchSize;

    public OutboxRelay(OutboxRepository repository, RabbitTemplate rabbitTemplate,
                       PlatformTransactionManager transactionManager, Clock clock,
                       @Value("${tickethub.messaging.exchange}") String exchange,
                       @Value("${tickethub.outbox.batch-size}") int batchSize) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.exchange = exchange;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${tickethub.outbox.relay-interval}")
    public void run() {
        try {
            int published;
            do {
                published = tx.execute(status -> publishBatch());
            } while (published == batchSize);
        } catch (RuntimeException e) {
            log.warn("Outbox relay failed, events stay pending until the next run: {}", e.getMessage());
        }
    }

    private int publishBatch() {
        var events = repository.lockUnpublished(batchSize);
        if (events.isEmpty()) {
            return 0;
        }
        rabbitTemplate.invoke(operations -> {
            events.forEach(event -> operations.send(exchange, event.getEventType(), toMessage(event)));
            operations.waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);
            return null;
        });
        var now = clock.instant();
        events.forEach(event -> event.markPublished(now));
        log.debug("Published {} outbox event(s)", events.size());
        return events.size();
    }

    private static Message toMessage(OutboxEvent event) {
        return MessageBuilder.withBody(event.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setMessageId(event.getId().toString())
                .setType(event.getEventType())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .build();
    }
}
