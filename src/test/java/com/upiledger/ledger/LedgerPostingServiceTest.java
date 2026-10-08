package com.upiledger.ledger;

import com.upiledger.accounts.AccountBalance;
import com.upiledger.accounts.AccountBalanceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LedgerPostingServiceTest {

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private AccountBalanceRepository accountBalanceRepository;

    @Test
    void postsBalancedTransferAndUpdatesBothBalances() {
        UUID payerId = UUID.randomUUID();
        UUID payeeId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();

        AccountBalance payer = new AccountBalance(payerId, "INR");
        payer.applyCredit(new BigDecimal("1000.00"));
        AccountBalance payee = new AccountBalance(payeeId, "INR");
        payer.reserve(new BigDecimal("500.00"));

        when(accountBalanceRepository.findByIdForUpdate(payerId)).thenReturn(Optional.of(payer));
        when(accountBalanceRepository.findByIdForUpdate(payeeId)).thenReturn(Optional.of(payee));

        LedgerPostingCommand command = new LedgerPostingCommand(
                transactionId,
                "INR",
                LedgerEntryReason.SETTLEMENT,
                java.util.List.of(
                        new LedgerPostingLine(payerId, LedgerEntryType.DEBIT, new BigDecimal("500.00")),
                        new LedgerPostingLine(payeeId, LedgerEntryType.CREDIT, new BigDecimal("500.00"))
                )
        );

        LedgerPostingService service = new LedgerPostingService(ledgerEntryRepository, accountBalanceRepository);

        UUID postingId = service.post(command);

        assertNotNull(postingId);
        assertEquals(new BigDecimal("500.00"), payer.getCommittedBalance());
        assertEquals(new BigDecimal("500.00"), payer.getAvailableBalance());
        assertEquals(new BigDecimal("500.00"), payee.getCommittedBalance());
        assertEquals(new BigDecimal("500.00"), payee.getAvailableBalance());
        verify(accountBalanceRepository, times(2)).save(any(AccountBalance.class));
        verify(ledgerEntryRepository, times(2)).save(any(LedgerEntry.class));

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, times(2)).save(captor.capture());
        assertEquals(2, captor.getAllValues().size());
        assertEquals(postingId, captor.getAllValues().get(0).getPostingId());
        assertEquals(postingId, captor.getAllValues().get(1).getPostingId());
    }

    @Test
    void rejectsUnbalancedPostingBeforeLockingAccounts() {
        UUID accountA = UUID.randomUUID();
        UUID accountB = UUID.randomUUID();

        LedgerPostingCommand command = new LedgerPostingCommand(
                UUID.randomUUID(),
                "INR",
                LedgerEntryReason.SETTLEMENT,
                java.util.List.of(
                        new LedgerPostingLine(accountA, LedgerEntryType.DEBIT, new BigDecimal("500.00")),
                        new LedgerPostingLine(accountB, LedgerEntryType.CREDIT, new BigDecimal("499.00"))
                )
        );

        LedgerPostingService service = new LedgerPostingService(ledgerEntryRepository, accountBalanceRepository);

        assertThrows(UnbalancedPostingException.class, () -> service.post(command));
        verifyNoInteractions(accountBalanceRepository, ledgerEntryRepository);
    }

    @Test
    void rejectsDebitWhenAvailableBalanceIsInsufficient() {
        UUID payerId = UUID.randomUUID();
        UUID payeeId = UUID.randomUUID();

        AccountBalance payer = new AccountBalance(payerId, "INR");
        AccountBalance payee = new AccountBalance(payeeId, "INR");
       

        when(accountBalanceRepository.findByIdForUpdate(payerId)).thenReturn(Optional.of(payer));
        when(accountBalanceRepository.findByIdForUpdate(payeeId)).thenReturn(Optional.of(payee));

        LedgerPostingCommand command = new LedgerPostingCommand(
                UUID.randomUUID(),
                "INR",
                LedgerEntryReason.SETTLEMENT,
                java.util.List.of(
                        new LedgerPostingLine(payerId, LedgerEntryType.DEBIT, new BigDecimal("500.00")),
                        new LedgerPostingLine(payeeId, LedgerEntryType.CREDIT, new BigDecimal("500.00"))
                )
        );

        LedgerPostingService service = new LedgerPostingService(ledgerEntryRepository, accountBalanceRepository);

        assertThrows(RuntimeException.class, () -> service.post(command));
        verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
    }
}
