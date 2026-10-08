package com.upiledger.eventing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OutboxEventServiceTest {
    @Test
    void enqueueSerializesPayloadAndCreatesPendingEvent() {
        var repository = mock(OutboxEventRepository.class);
        var service = new OutboxEventService(repository, new ObjectMapper());
        when(repository.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UUID aggregateId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        OutboxEvent result = service.enqueue("PAYMENT", aggregateId, "PAYMENT_INITIATED", java.util.Map.of("amount", 500, "currency", "INR"), correlationId);
        assertEquals(OutboxStatus.PENDING, result.getStatus());
        assertEquals(0, result.getAttemptCount());
        assertEquals(aggregateId, result.getAggregateId());
        assertEquals(correlationId, result.getCorrelationId());
        assertEquals(500, result.getPayload().get("amount").asInt());
        assertEquals("INR", result.getPayload().get("currency").asText());
        verify(repository).save(any(OutboxEvent.class));
    }
}
