package com.upiledger.eventing;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxPublisherTest {
    @Test
    void publishesPendingEventAndMarksItPublished() throws Exception {
        var repository = mock(OutboxEventRepository.class);
        var kafka = mock(KafkaTemplate.class);
        var serializer = mock(OutboxEventService.class);
        var topics = mock(KafkaTopics.class);
        UUID eventId = UUID.randomUUID();
        UUID aggregateId = UUID.randomUUID();
        var event = new OutboxEvent("PAYMENT", aggregateId, "PAYMENT_INITIATED", new ObjectMapperHelper().json("{\"amount\":500}"), UUID.randomUUID());
        setId(event, eventId);
        when(topics.topicFor("PAYMENT_INITIATED")).thenReturn("upiledger.payment.lifecycle.v1");
        when(serializer.serialize(any())).thenReturn("{}");
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(null);
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(future);
        var publisher = new OutboxPublisher(repository, kafka, serializer, topics);
        publisher.publishOne(event);
        assertEquals(OutboxStatus.PUBLISHED, event.getStatus());
        assertNotNull(event.getPublishedAt());
        verify(kafka).send(eq("upiledger.payment.lifecycle.v1"), eq(aggregateId.toString()), eq("{}"));
        verify(repository).save(event);
    }

    @Test
    void recordsFailureForKafkaPublishError() throws Exception {
        var repository = mock(OutboxEventRepository.class);
        var kafka = mock(KafkaTemplate.class);
        var serializer = mock(OutboxEventService.class);
        var topics = mock(KafkaTopics.class);
        var event = new OutboxEvent("PAYMENT", UUID.randomUUID(), "PAYMENT_SETTLED", new ObjectMapperHelper().json("{\"amount\":500}"), UUID.randomUUID());
        setId(event, UUID.randomUUID());
        when(topics.topicFor("PAYMENT_SETTLED")).thenReturn("upiledger.payment.settled.v1");
        when(serializer.serialize(any())).thenReturn("{}");
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(new IllegalStateException("Kafka unavailable"));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(future);
        var publisher = new OutboxPublisher(repository, kafka, serializer, topics);
        publisher.publishOne(event);
        assertEquals(OutboxStatus.FAILED, event.getStatus());
        assertEquals(1, event.getAttemptCount());
        assertTrue(event.getLastError().contains("Kafka unavailable"));
        verify(repository).save(event);
    }

    private static void setId(OutboxEvent event, UUID id) throws Exception {
        Field field = OutboxEvent.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(event, id);
    }

    private static final class ObjectMapperHelper {
        private com.fasterxml.jackson.databind.JsonNode json(String value) throws Exception {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(value);
        }
    }
}
