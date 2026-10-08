package com.upiledger.reconciliation;

import com.upiledger.accounts.*;
import com.upiledger.ledger.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReconciliationServiceTest {

    @Test
    void passesWhenLedgerProjectionAndActiveHoldsMatch() throws Exception {
        var batches = mock(ReconciliationBatchRepository.class);
        var discrepancies = mock(ReconciliationDiscrepancyRepository.class);
        var accounts = mock(AccountRepository.class);
        var balances = mock(AccountBalanceRepository.class);
        var ledger = mock(LedgerEntryRepository.class);
        var holds = mock(AccountHoldRepository.class);

        UUID accountId = UUID.randomUUID();
        var account = new Account("acct-1", AccountType.WALLET, "INR");
        setId(Account.class, account, accountId);

        var balance = new AccountBalance(accountId, "INR");
        balance.applyCredit(new BigDecimal("1000"));
        balance.reserve(new BigDecimal("200"));

        var batch = new ReconciliationBatch(ReconciliationStatus.RUNNING);
        setId(ReconciliationBatch.class, batch, UUID.randomUUID());

        when(batches.save(any(ReconciliationBatch.class))).thenReturn(batch);
        when(accounts.findAll()).thenReturn(List.of(account));
        when(balances.findById(accountId)).thenReturn(Optional.of(balance));
        when(ledger.findAll()).thenReturn(List.of(
                new LedgerEntry(UUID.randomUUID(), UUID.randomUUID(), accountId,
                        "INR", LedgerEntryType.CREDIT, new BigDecimal("1000"),
                        LedgerEntryReason.SETTLEMENT)));
        var activeHold = mockHold(accountId, new BigDecimal("200"));
        when(holds.findByStatus(HoldStatus.ACTIVE)).thenReturn(List.of(activeHold));

        var service = new ReconciliationService(
                batches, discrepancies, accounts, balances, ledger, holds);

        var result = service.run();

        assertEquals(ReconciliationStatus.COMPLETED, result.getStatus());
        verify(discrepancies, never()).save(any());
    }

    @Test
    void recordsCommittedAndAvailableDiscrepancies() throws Exception {
        var batches = mock(ReconciliationBatchRepository.class);
        var discrepancies = mock(ReconciliationDiscrepancyRepository.class);
        var accounts = mock(AccountRepository.class);
        var balances = mock(AccountBalanceRepository.class);
        var ledger = mock(LedgerEntryRepository.class);
        var holds = mock(AccountHoldRepository.class);

        UUID accountId = UUID.randomUUID();
        var account = new Account("acct-2", AccountType.WALLET, "INR");
        setId(Account.class, account, accountId);

        var balance = new AccountBalance(accountId, "INR");
        balance.applyCredit(new BigDecimal("700"));

        var batch = new ReconciliationBatch(ReconciliationStatus.RUNNING);
        setId(ReconciliationBatch.class, batch, UUID.randomUUID());

        when(batches.save(any(ReconciliationBatch.class))).thenReturn(batch);
        when(accounts.findAll()).thenReturn(List.of(account));
        when(balances.findById(accountId)).thenReturn(Optional.of(balance));
        when(ledger.findAll()).thenReturn(List.of(
                new LedgerEntry(UUID.randomUUID(), UUID.randomUUID(), accountId,
                        "INR", LedgerEntryType.CREDIT, new BigDecimal("1000"),
                        LedgerEntryReason.SETTLEMENT)));
        var activeHold = mockHold(accountId, new BigDecimal("100"));
        when(holds.findByStatus(HoldStatus.ACTIVE)).thenReturn(List.of(activeHold));

        var service = new ReconciliationService(
                batches, discrepancies, accounts, balances, ledger, holds);

        service.run();

        verify(discrepancies, times(2)).save(any(ReconciliationDiscrepancy.class));
    }

    private static AccountHold mockHold(UUID accountId, BigDecimal amount) {
        var hold = mock(AccountHold.class);
        when(hold.getAccountId()).thenReturn(accountId);
        when(hold.getAmount()).thenReturn(amount);
        return hold;
    }

    private static void setId(Class<?> type, Object target, UUID id) throws Exception {
        var field = type.getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }
}
