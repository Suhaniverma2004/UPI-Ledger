package com.upiledger.reconciliation;

import com.upiledger.accounts.*;
import com.upiledger.ledger.LedgerEntry;
import com.upiledger.ledger.LedgerEntryRepository;
import com.upiledger.ledger.LedgerEntryType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
public class ReconciliationService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final ReconciliationBatchRepository batches;
    private final ReconciliationDiscrepancyRepository discrepancies;
    private final AccountRepository accounts;
    private final AccountBalanceRepository balances;
    private final LedgerEntryRepository ledgerEntries;
    private final AccountHoldRepository holds;

    public ReconciliationService(
            ReconciliationBatchRepository batches,
            ReconciliationDiscrepancyRepository discrepancies,
            AccountRepository accounts,
            AccountBalanceRepository balances,
            LedgerEntryRepository ledgerEntries,
            AccountHoldRepository holds) {
        this.batches = batches;
        this.discrepancies = discrepancies;
        this.accounts = accounts;
        this.balances = balances;
        this.ledgerEntries = ledgerEntries;
        this.holds = holds;
    }

    @Transactional
    public ReconciliationBatch run() {
        ReconciliationBatch batch = batches.save(new ReconciliationBatch(ReconciliationStatus.RUNNING));

        try {
            Map<UUID, BigDecimal> expectedCommitted = calculateExpectedCommitted();
            Map<UUID, BigDecimal> activeHolds = calculateActiveHolds();

            for (Account account : accounts.findAll()) {
                AccountBalance actual = balances.findById(account.getId()).orElse(null);
                if (actual == null) {
                    discrepancies.save(new ReconciliationDiscrepancy(
                            batch.getId(), account.getId(), ZERO, ZERO,
                            "Missing account balance projection"));
                    continue;
                }

                BigDecimal expectedCommittedBalance =
                        expectedCommitted.getOrDefault(account.getId(), ZERO);
                BigDecimal expectedAvailableBalance =
                        expectedCommittedBalance.subtract(
                                activeHolds.getOrDefault(account.getId(), ZERO));

                recordIfDifferent(batch, account.getId(),
                        expectedCommittedBalance, actual.getCommittedBalance(),
                        "Committed balance mismatch");

                recordIfDifferent(batch, account.getId(),
                        expectedAvailableBalance, actual.getAvailableBalance(),
                        "Available balance mismatch");
            }

            batch.complete();
            return batches.save(batch);
        } catch (RuntimeException ex) {
            batch.fail();
            batches.save(batch);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public ReconciliationBatch get(UUID batchId) {
        return batches.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Reconciliation batch not found: " + batchId));
    }

    @Transactional(readOnly = true)
    public List<ReconciliationDiscrepancy> getDiscrepancies(UUID batchId) {
        return discrepancies.findAll().stream()
                .filter(d -> batchId.equals(d.getBatchId()))
                .toList();
    }

    private Map<UUID, BigDecimal> calculateExpectedCommitted() {
        Map<UUID, BigDecimal> totals = new HashMap<>();

        for (LedgerEntry entry : ledgerEntries.findAll()) {
            BigDecimal signedAmount = entry.getEntryType() == LedgerEntryType.CREDIT
                    ? entry.getAmount()
                    : entry.getAmount().negate();

            totals.merge(entry.getAccountId(), signedAmount, BigDecimal::add);
        }

        return totals;
    }

    private Map<UUID, BigDecimal> calculateActiveHolds() {
        Map<UUID, BigDecimal> totals = new HashMap<>();

        for (AccountHold hold : holds.findByStatus(HoldStatus.ACTIVE)) {
            totals.merge(hold.getAccountId(), hold.getAmount(), BigDecimal::add);
        }

        return totals;
    }

    private void recordIfDifferent(
            ReconciliationBatch batch,
            UUID accountId,
            BigDecimal expected,
            BigDecimal actual,
            String description) {

        if (expected.compareTo(actual) != 0) {
            discrepancies.save(new ReconciliationDiscrepancy(
                    batch.getId(), accountId, expected, actual, description));
        }
    }
}
