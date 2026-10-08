package com.upiledger.api;

import com.upiledger.reconciliation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reconciliation")
public class ReconciliationController {

    private final ReconciliationService service;

    public ReconciliationController(ReconciliationService service) {
        this.service = service;
    }

    @PostMapping("/run")
    public ReconciliationBatchResponse run() {
        return ReconciliationBatchResponse.from(service.run());
    }

    @GetMapping("/{batchId}")
    public ReconciliationReportResponse get(@PathVariable UUID batchId) {
        var batch = service.get(batchId);
        var items = service.getDiscrepancies(batchId).stream()
                .map(ReconciliationDiscrepancyResponse::from)
                .toList();
        return new ReconciliationReportResponse(
                ReconciliationBatchResponse.from(batch), items);
    }

    public record ReconciliationBatchResponse(
            UUID batchId,
            ReconciliationStatus status,
            Instant startedAt,
            Instant completedAt) {
        static ReconciliationBatchResponse from(ReconciliationBatch batch) {
            return new ReconciliationBatchResponse(
                    batch.getId(), batch.getStatus(),
                    batch.getStartedAt(), batch.getCompletedAt());
        }
    }

    public record ReconciliationDiscrepancyResponse(
            UUID id,
            UUID accountId,
            BigDecimal expectedBalance,
            BigDecimal actualBalance,
            String description) {
        static ReconciliationDiscrepancyResponse from(ReconciliationDiscrepancy d) {
            return new ReconciliationDiscrepancyResponse(
                    d.getId(), d.getAccountId(),
                    d.getExpectedBalance(), d.getActualBalance(),
                    d.getDescription());
        }
    }

    public record ReconciliationReportResponse(
            ReconciliationBatchResponse batch,
            List<ReconciliationDiscrepancyResponse> discrepancies) {}
}
