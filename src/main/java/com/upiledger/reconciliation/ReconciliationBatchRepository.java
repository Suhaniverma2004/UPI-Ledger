package com.upiledger.reconciliation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface ReconciliationBatchRepository extends JpaRepository<ReconciliationBatch, UUID> {}
