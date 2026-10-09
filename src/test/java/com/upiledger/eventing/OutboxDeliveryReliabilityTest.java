package com.upiledger.eventing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class OutboxDeliveryReliabilityTest {

    @Test
    void pendingEventIsEligibleForFirstPublication() {
        OutboxEvent event = event();

        assertTrue(event.isRetryEligible(Instant.now(), Duration.ofSeconds(5), 5));
    }

    @Test
    void failedEventIsNotEligibleBeforeBackoffExpires() {
        OutboxEvent event = event();
        Instant attemptedAt = Instant.parse("2026-10-09T10:00:00Z");
        event.markFailed(attemptedAt, "broker unavailable");

        assertFalse(event.isRetryEligible(
                attemptedAt.plusSeconds(4), Duration.ofSeconds(5), 5));
    }

    @Test
    void failedEventBecomesEligibleAfterFirstBackoff() {
        OutboxEvent event = event();
        Instant attemptedAt = Instant.parse("2026-10-09T10:00:00Z");
        event.markFailed(attemptedAt, "broker unavailable");

        assertTrue(event.isRetryEligible(
                attemptedAt.plusSeconds(5), Duration.ofSeconds(5), 5));
    }

    @Test
    void failedEventStopsBeingEligibleAtMaximumAttempts() {
        OutboxEvent event = event();
        Instant attempt = Instant.parse("2026-10-09T10:00:00Z");
        for (int i = 0; i < 5; i++) {
            event.markFailed(attempt.plusSeconds(i * 100L), "broker unavailable");
        }

        assertFalse(event.isRetryEligible(
                attempt.plusSeconds(10_000), Duration.ofSeconds(1), 5));
    }

    @Test
    void publishedEventIsNotEligibleForAnotherAttempt() {
        OutboxEvent event = event();
        event.markPublished(Instant.parse("2026-10-09T10:01:00Z"));

        assertFalse(event.isRetryEligible(
                Instant.parse("2026-10-09T10:02:00Z"), Duration.ofSeconds(1), 5));
    }

    private static OutboxEvent event() {
        return new OutboxEvent(
                "PAYMENT",
                java.util.UUID.randomUUID(),
                "PAYMENT_SETTLED",
                new ObjectMapper().createObjectNode().put("transactionId", "test-txn"),
                java.util.UUID.randomUUID()
        );
    }
}
