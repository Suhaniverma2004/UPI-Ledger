package com.upiledger.eventing;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class OutboxEventHardeningTest {

    @Test
    void processingStateIsNotRetryEligible() {
        var event = new OutboxEvent();
        setStatus(event, OutboxStatus.PROCESSING);

        assertFalse(event.isRetryEligible(
                Instant.now(),
                Duration.ofSeconds(5),
                5
        ));
    }

    @Test
    void releaseForRetryMovesProcessingEventToFailed() {
        var event = new OutboxEvent();
        Instant attempt = Instant.parse("2026-10-09T01:00:00Z");

        event.markProcessing(attempt);
        event.releaseForRetry(attempt.plusSeconds(1), "Kafka unavailable");

        assertFalse(event.isRetryEligible(
                attempt.plusSeconds(2),
                Duration.ofSeconds(5),
                5
        ));
    }

    private static void setStatus(OutboxEvent event, OutboxStatus status) {
        try {
            var field = OutboxEvent.class.getDeclaredField("status");
            field.setAccessible(true);
            field.set(event, status);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
