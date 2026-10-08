package com.upiledger.eventing;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox", indexes = {
        @Index(name = "idx_outbox_status_created", columnList = "status,created_at")
})
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 60)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private JsonNode payload;

    @Column(name = "correlation_id")
    private UUID correlationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(String aggregateType, UUID aggregateId, String eventType,
                       JsonNode payload, UUID correlationId) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.correlationId = correlationId;
        this.status = OutboxStatus.PENDING;
        this.attemptCount = 0;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getAggregateType() { return aggregateType; }
    public UUID getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public JsonNode getPayload() { return payload; }
    public UUID getCorrelationId() { return correlationId; }
    public OutboxStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getLastAttemptAt() { return lastAttemptAt; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }

    public void markPublished(Instant publishedAt) {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = publishedAt;
        this.lastError = null;
    }

    public void markFailed(Instant attemptAt, String error) {
        this.status = OutboxStatus.FAILED;
        this.attemptCount++;
        this.lastAttemptAt = attemptAt;
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 2000));
    }

    public boolean isRetryEligible(
            Instant now,
            Duration baseRetryDelay,
            int maxAttempts
    ) {
        if (status == OutboxStatus.PENDING) {
            return true;
        }

        if (status != OutboxStatus.FAILED || lastAttemptAt == null) {
            return false;
        }

        if (attemptCount >= maxAttempts) {
            return false;
        }

        long multiplier = 1L << Math.min(Math.max(attemptCount - 1, 0), 30);
        Duration retryDelay = baseRetryDelay.multipliedBy(multiplier);

        return !lastAttemptAt.plus(retryDelay).isAfter(now);
    }
}
