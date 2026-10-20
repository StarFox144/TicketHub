package com.tickethub.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Записати подію в outbox. MANDATORY: подія має закомітитися разом зі зміною стану,
 * яку вона описує, — інакше зникає весь сенс патерну.
 */
@Service
public class OutboxPublisher {

    private final OutboxRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxPublisher(OutboxRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String aggregateType, UUID aggregateId, String eventType, Object payload) {
        try {
            var json = objectMapper.writeValueAsString(payload);
            repository.save(new OutboxEvent(aggregateType, aggregateId, eventType, json));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Outbox payload is not serializable: " + payload, e);
        }
    }
}
