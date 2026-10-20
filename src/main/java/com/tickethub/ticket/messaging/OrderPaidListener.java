package com.tickethub.ticket.messaging;

import com.tickethub.booking.messaging.OrderPaid;
import com.tickethub.ticket.service.TicketService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Генерація QR, PDF і відправка листа — повільна робота, тому винесена з HTTP-запиту webhook-а.
 * Виняток тут → ретраї з backoff (spring.rabbitmq.listener.simple.retry) → DLQ.
 */
@Component
public class OrderPaidListener {

    private final TicketService ticketService;

    public OrderPaidListener(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @RabbitListener(queues = TicketMessagingConfig.GENERATE_QUEUE)
    public void onOrderPaid(OrderPaid event) {
        ticketService.issueAndSend(event.orderId());
    }
}
