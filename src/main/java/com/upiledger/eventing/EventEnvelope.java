package com.upiledger.eventing;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/**
 * Stable Kafka event contract used by UPI-Ledger producers and consumers.
 */
public record EventEnvelope(
        UUID eventId,
        String eventType,
        String aggregateType,
        UUID aggregateId,
        Instant occurredAt,
        UUID correlationId,
        JsonNode payload
) {
}
