package com.tickethub.ticket.domain;

import com.tickethub.common.exception.ConflictException;

import java.time.Instant;
import java.util.Map;

/** Повторне сканування: контролер на вході бачить, коли саме квиток уже пройшов. */
public class TicketAlreadyUsedException extends ConflictException {

    private final Instant usedAt;

    public TicketAlreadyUsedException(Instant usedAt) {
        super("Ticket has already been used");
        this.usedAt = usedAt;
    }

    @Override
    public Map<String, Object> properties() {
        return Map.of("usedAt", usedAt);
    }
}
