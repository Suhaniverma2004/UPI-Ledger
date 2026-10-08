package com.upiledger.eventing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OutboxEventService {
    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxEventService(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * The outbox write must participate in the caller's database transaction.
     * Propagation.MANDATORY prevents accidental use outside a transaction.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent enqueue(String aggregateType, UUID aggregateId, String eventType,
                               Object payload, UUID correlationId) {
        try {
            var jsonPayload = objectMapper.valueToTree(payload);
            return repository.save(new OutboxEvent(
                    aggregateType,
                    aggregateId,
                    eventType,
                    jsonPayload,
                    correlationId
            ));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Unable to serialize outbox event payload", e);
        }
    }

    public String serialize(EventEnvelope event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize Kafka event", e);
        }
    }
}
