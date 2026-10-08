package com.upiledger.eventing;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class OutboxEventTest {

    @Test
    void pendingEventIsImmediatelyRetryEligible() {
        var event = new OutboxEvent();
        setStatus(event, OutboxStatus.PENDING);

        assertTrue(event.isRetryEligible(
                Instant.parse("2026-10-09T01:00:00Z"),
                Duration.ofSeconds(5)
        ));
    }

    @Test
    void failedEventIsNotEligibleBeforeRetryDelay() {
        var event = new OutboxEvent();
        Instant attempt = Instant.parse("2026-10-09T01:00:00Z");

        event.markFailed(attempt, "Kafka unavailable");

        assertFalse(event.isRetryEligible(
                Instant.parse("2026-10-09T01:00:04Z"),
                Duration.ofSeconds(5)
        ));
    }

    @Test
    void failedEventIsEligibleAtRetryDelayBoundary() {
        var event = new OutboxEvent();
        Instant attempt = Instant.parse("2026-10-09T01:00:00Z");

        event.markFailed(attempt, "Kafka unavailable");

        assertTrue(event.isRetryEligible(
                Instant.parse("2026-10-09T01:00:05Z"),
                Duration.ofSeconds(5)
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
