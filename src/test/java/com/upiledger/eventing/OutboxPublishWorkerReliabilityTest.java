package com.upiledger.eventing;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxPublishWorkerReliabilityTest {

    @Test
    void successfulKafkaSendMarksEventPublished() throws Exception {
        var repository = mock(OutboxEventRepository.class);
        var kafka = mock(KafkaTemplate.class);
        var eventService = mock(OutboxEventService.class);
        var topics = topics();
        var worker = worker(repository, kafka, eventService, topics);
        var event = event();

        when(eventService.serialize(any(EventEnvelope.class))).thenReturn("{\"eventType\":\"PAYMENT_SETTLED\"}");
        when(kafka.send(eq(topics.paymentSettled()), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        assertTrue(worker.claimAndPublish(event));

        assertEquals(OutboxStatus.PUBLISHED, event.getStatus());
        assertNotNull(event.getPublishedAt());
        assertNull(event.getLastError());
        verify(repository).saveAndFlush(event);
        verify(repository).save(event);
        verify(kafka).send(eq(topics.paymentSettled()), eq(event.getAggregateId().toString()),
                eq("{\"eventType\":\"PAYMENT_SETTLED\"}"));
    }

    @Test
    void kafkaFailureMarksEventFailedAndRecordsRootError() throws Exception {
        var repository = mock(OutboxEventRepository.class);
        var kafka = mock(KafkaTemplate.class);
        var eventService = mock(OutboxEventService.class);
        var topics = topics();
        var worker = worker(repository, kafka, eventService, topics);
        var event = event();

        when(eventService.serialize(any(EventEnvelope.class))).thenReturn("{\"eventType\":\"PAYMENT_SETTLED\"}");
        when(kafka.send(eq(topics.paymentSettled()), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(
                        new IllegalStateException("broker unavailable")));

        assertFalse(worker.claimAndPublish(event));

        assertEquals(OutboxStatus.FAILED, event.getStatus());
        assertEquals(1, event.getAttemptCount());
        assertEquals("broker unavailable", event.getLastError());
        assertNotNull(event.getLastAttemptAt());
        verify(repository).saveAndFlush(event);
        verify(repository).save(event);
    }

    @Test
    void processingEventIsNotPublishedAgain() throws Exception {
        var repository = mock(OutboxEventRepository.class);
        var kafka = mock(KafkaTemplate.class);
        var eventService = mock(OutboxEventService.class);
        var worker = worker(repository, kafka, eventService, topics());
        var event = event();
        event.markProcessing(Instant.now());

        assertFalse(worker.claimAndPublish(event));

        verifyNoInteractions(kafka);
        verifyNoInteractions(eventService);
        verify(repository, never()).saveAndFlush(any(OutboxEvent.class));
    }

    private static OutboxPublishWorker worker(
            OutboxEventRepository repository,
            KafkaTemplate<String, String> kafka,
            OutboxEventService eventService,
            KafkaTopics topics) throws Exception {
        var worker = new OutboxPublishWorker(repository, kafka, eventService, topics);
        setField(worker, "retryDelayMs", 5000L);
        setField(worker, "maxAttempts", 5);
        return worker;
    }

    private static OutboxEvent event() {
        return new OutboxEvent(
                "PAYMENT",
                UUID.randomUUID(),
                "PAYMENT_SETTLED",
                new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode()
                        .put("transactionId", "v86-test"),
                UUID.randomUUID());
    }

    private static KafkaTopics topics() {
        return new KafkaTopics(
                "upiledger.payment.lifecycle.v1",
                "upiledger.ledger.entry.posted.v1",
                "upiledger.payment.settled.v1",
                "upiledger.payment.failed.v1",
                "upiledger.payment.reversed.v1",
                "upiledger.dlq.v1");
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
