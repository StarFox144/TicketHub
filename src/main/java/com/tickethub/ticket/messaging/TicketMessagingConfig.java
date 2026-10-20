package com.tickethub.ticket.messaging;

import com.tickethub.booking.messaging.OrderPaid;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * tickets (topic) --order.paid--> ticket.generate --після 3 невдалих ретраїв--> tickets.dlx --> ticket.generate.dlq
 * <p>
 * Повідомлення в DLQ не губляться: їх можна роздивитися в консолі RabbitMQ (localhost:15672)
 * і повернути в основну чергу після виправлення причини (shovel або вручну).
 */
@Configuration
public class TicketMessagingConfig {

    public static final String GENERATE_QUEUE = "ticket.generate";
    public static final String DEAD_LETTER_QUEUE = "ticket.generate.dlq";
    public static final String DEAD_LETTER_EXCHANGE = "tickets.dlx";

    @Bean
    Declarables ticketTopology(@Value("${tickethub.messaging.exchange}") String exchangeName) {
        var events = new TopicExchange(exchangeName, true, false);
        var deadLetters = new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
        var generate = QueueBuilder.durable(GENERATE_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(DEAD_LETTER_QUEUE)
                .build();
        var deadLetterQueue = QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
        return new Declarables(events, deadLetters, generate, deadLetterQueue,
                BindingBuilder.bind(generate).to(events).with(OrderPaid.EVENT_TYPE),
                BindingBuilder.bind(deadLetterQueue).to(deadLetters).with(DEAD_LETTER_QUEUE));
    }
}
