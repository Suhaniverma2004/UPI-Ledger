package com.upiledger.reconciliation;

import com.upiledger.accounts.*;
import com.upiledger.ledger.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReconciliationRecoveryTest {

    @Test
    void recordsMissingBalanceProjectionAndCompletesBatch() throws Exception {
        var batches = mock(ReconciliationBatchRepository.class);
        var discrepancies = mock(ReconciliationDiscrepancyRepository.class);
        var accounts = mock(AccountRepository.class);
        var balances = mock(AccountBalanceRepository.class);
        var ledger = mock(LedgerEntryRepository.class);
        var holds = mock(AccountHoldRepository.class);

        UUID accountId = UUID.randomUUID();
        var account = new Account("recovery-account", AccountType.WALLET, "INR");
        setId(Account.class, account, accountId);

        var batch = new ReconciliationBatch(ReconciliationStatus.RUNNING);
        setId(ReconciliationBatch.class, batch, UUID.randomUUID());

        when(batches.save(any(ReconciliationBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accounts.findAll()).thenReturn(List.of(account));
        when(balances.findById(accountId)).thenReturn(Optional.empty());
        when(ledger.findAll()).thenReturn(List.of());
        when(holds.findByStatus(HoldStatus.ACTIVE)).thenReturn(List.of());

        var service = new ReconciliationService(
                batches, discrepancies, accounts, balances, ledger, holds);

        var result = service.run();

        assertEquals(ReconciliationStatus.COMPLETED, result.getStatus());
        verify(discrepancies).save(argThat(discrepancy ->
                accountId.equals(discrepancy.getAccountId())
                        && "Missing account balance projection".equals(discrepancy.getDescription())));
    }

    @Test
    void reconcilesDebitEntriesAsNegativeAmounts() throws Exception {
        var batches = mock(ReconciliationBatchRepository.class);
        var discrepancies = mock(ReconciliationDiscrepancyRepository.class);
        var accounts = mock(AccountRepository.class);
        var balances = mock(AccountBalanceRepository.class);
        var ledger = mock(LedgerEntryRepository.class);
        var holds = mock(AccountHoldRepository.class);

        UUID accountId = UUID.randomUUID();
        var account = new Account("debit-account", AccountType.WALLET, "INR");
        setId(Account.class, account, accountId);

        var balance = new AccountBalance(accountId, "INR");
        balance.applyCredit(new BigDecimal("1000"));
        balance.applyDebit(new BigDecimal("300"));

        var batch = new ReconciliationBatch(ReconciliationStatus.RUNNING);
        setId(ReconciliationBatch.class, batch, UUID.randomUUID());

        when(batches.save(any(ReconciliationBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accounts.findAll()).thenReturn(List.of(account));
        when(balances.findById(accountId)).thenReturn(Optional.of(balance));
        when(ledger.findAll()).thenReturn(List.of(
                new LedgerEntry(UUID.randomUUID(), UUID.randomUUID(), accountId,
                        "INR", LedgerEntryType.CREDIT, new BigDecimal("1000"),
                        LedgerEntryReason.SETTLEMENT),
                new LedgerEntry(UUID.randomUUID(), UUID.randomUUID(), accountId,
                        "INR", LedgerEntryType.DEBIT, new BigDecimal("300"),
                        LedgerEntryReason.SETTLEMENT)));
        when(holds.findByStatus(HoldStatus.ACTIVE)).thenReturn(List.of());

        var service = new ReconciliationService(
                batches, discrepancies, accounts, balances, ledger, holds);

        var result = service.run();

        assertEquals(ReconciliationStatus.COMPLETED, result.getStatus());
        verify(discrepancies, never()).save(any(ReconciliationDiscrepancy.class));
    }

    private static void setId(Class<?> type, Object target, UUID id) throws Exception {
        var field = type.getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }
}
