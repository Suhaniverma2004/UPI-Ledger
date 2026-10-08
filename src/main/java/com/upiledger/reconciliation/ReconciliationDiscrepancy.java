package com.upiledger.reconciliation;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_discrepancies", indexes = {
    @Index(name = "idx_recon_discrepancy_batch", columnList = "batch_id")
})
public class ReconciliationDiscrepancy {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "account_id")
    private UUID accountId;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal expectedBalance;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal actualBalance;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ReconciliationDiscrepancy() {}
}
