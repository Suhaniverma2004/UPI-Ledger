package com.upiledger.reconciliation;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_batches")
public class ReconciliationBatch {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReconciliationStatus status;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ReconciliationBatch() {}

    public ReconciliationBatch(ReconciliationStatus status) {
        this.status = status;
        this.startedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public ReconciliationStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }

    public void complete() {
        this.status = ReconciliationStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void fail() {
        this.status = ReconciliationStatus.FAILED;
        this.completedAt = Instant.now();
    }
}
