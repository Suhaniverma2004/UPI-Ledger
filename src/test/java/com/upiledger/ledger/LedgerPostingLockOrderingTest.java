package com.upiledger.ledger;

import com.upiledger.accounts.AccountBalance;
import com.upiledger.accounts.AccountBalanceRepository;
import com.upiledger.eventing.OutboxEventService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

class LedgerPostingLockOrderingTest {

    @Test
    void locksAccountsInSortedUuidOrderRegardlessOfPostingLineOrder() {
        UUID lowerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID higherId = UUID.fromString("00000000-0000-0000-0000-000000000002");

        AccountBalance lowerBalance = new AccountBalance(lowerId, "INR");
        lowerBalance.applyCredit(new BigDecimal("100.00"));
        AccountBalance higherBalance = new AccountBalance(higherId, "INR");

        AccountBalanceRepository balances = mock(AccountBalanceRepository.class);
        LedgerEntryRepository entries = mock(LedgerEntryRepository.class);
        OutboxEventService outbox = mock(OutboxEventService.class);

        when(balances.findByIdForUpdate(lowerId)).thenReturn(Optional.of(lowerBalance));
        when(balances.findByIdForUpdate(higherId)).thenReturn(Optional.of(higherBalance));

        LedgerPostingCommand command = new LedgerPostingCommand(
                UUID.randomUUID(),
                "INR",
                LedgerEntryReason.SETTLEMENT,
                List.of(
                        new LedgerPostingLine(higherId, LedgerEntryType.CREDIT, new BigDecimal("10.00")),
                        new LedgerPostingLine(lowerId, LedgerEntryType.DEBIT, new BigDecimal("10.00"))
                )
        );

        LedgerPostingService service = new LedgerPostingService(entries, balances, outbox);
        UUID postingId = service.post(command);

        assertNotNull(postingId);
        InOrder lockOrder = inOrder(balances);
        lockOrder.verify(balances).findByIdForUpdate(lowerId);
        lockOrder.verify(balances).findByIdForUpdate(higherId);
        verify(balances, times(2)).findByIdForUpdate(any(UUID.class));
        verify(entries, times(2)).save(any(LedgerEntry.class));
        verify(outbox).enqueue(
                eq("LEDGER_POSTING"),
                eq(command.transactionId()),
                eq("LEDGER_ENTRY_POSTED"),
                any(),
                any(UUID.class)
        );
    }

    @Test
    void repeatedAccountAcrossLinesIsLockedOnlyOnce() {
        UUID accountId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        AccountBalance balance = new AccountBalance(accountId, "INR");
        balance.applyCredit(new BigDecimal("50.00"));

        AccountBalanceRepository balances = mock(AccountBalanceRepository.class);
        LedgerEntryRepository entries = mock(LedgerEntryRepository.class);
        OutboxEventService outbox = mock(OutboxEventService.class);
        when(balances.findByIdForUpdate(accountId)).thenReturn(Optional.of(balance));

        LedgerPostingCommand command = new LedgerPostingCommand(
                UUID.randomUUID(),
                "INR",
                LedgerEntryReason.SETTLEMENT,
                List.of(
                        new LedgerPostingLine(accountId, LedgerEntryType.DEBIT, new BigDecimal("10.00")),
                        new LedgerPostingLine(accountId, LedgerEntryType.CREDIT, new BigDecimal("10.00"))
                )
        );

        LedgerPostingService service = new LedgerPostingService(entries, balances, outbox);
        UUID postingId = service.post(command);

        assertNotNull(postingId);
        verify(balances, times(1)).findByIdForUpdate(accountId);
        verify(entries, times(2)).save(any(LedgerEntry.class));
    }
}
