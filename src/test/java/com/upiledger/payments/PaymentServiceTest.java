package com.upiledger.payments;

import com.upiledger.accounts.*;
import com.upiledger.eventing.OutboxEventService;
import com.upiledger.idempotency.*;
import com.upiledger.ledger.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentServiceTest {
    @Test
    void createIsIdempotentForSameKeyAndPayload() {
        var txRepo = mock(PaymentTransactionRepository.class);
        var bal = mock(AccountBalanceRepository.class);
        var hold = mock(AccountHoldRepository.class);
        var ledger = mock(LedgerPostingService.class);
        var idem = mock(IdempotencyKeyRepository.class);
        var outbox = mock(OutboxEventService.class);
        UUID p = UUID.randomUUID(), q = UUID.randomUUID(), id = UUID.randomUUID();
        var tx = new PaymentTransaction("ext-1", p, q, new BigDecimal("500"), "INR");
        try {
            var field = PaymentTransaction.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(tx, id);
        }catch (Exception e) {
            throw new RuntimeException(e);
        }
        when(idem.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(txRepo.findByExternalTxnId("ext-1")).thenReturn(Optional.empty());
        when(txRepo.save(any(PaymentTransaction.class))).thenAnswer(inv -> {
            var saved = inv.getArgument(0, PaymentTransaction.class);

            try {
                var field = PaymentTransaction.class.getDeclaredField("id");
                field.setAccessible(true);
                field.set(saved, id);
            } catch (Exception e) {
                throw new RuntimeException(e);
                }

            return saved;
        });
        when(idem.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentService s = new PaymentService(txRepo, bal, hold, ledger, idem, outbox);
        var result = s.create(new PaymentService.CreatePaymentCommand("ext-1", p, q,
                new BigDecimal("500"), "INR"), "key-1");

        assertNotNull(result);
        verify(txRepo).save(any(PaymentTransaction.class));
        verify(idem, times(2)).save(any(IdempotencyKey.class));
        verify(outbox).enqueue(eq("PAYMENT"), any(UUID.class), eq(PaymentEventTypes.INITIATED), any(), any(UUID.class));
    }

    @Test
    void authorizeReservesAvailableBalanceCreatesHoldAndEmitsEvent() throws Exception {
        var txRepo = mock(PaymentTransactionRepository.class);
        var balRepo = mock(AccountBalanceRepository.class);
        var holdRepo = mock(AccountHoldRepository.class);
        var ledger = mock(LedgerPostingService.class);
        var idem = mock(IdempotencyKeyRepository.class);
        var outbox = mock(OutboxEventService.class);
        UUID p = UUID.randomUUID(), q = UUID.randomUUID(), id = UUID.randomUUID();
        var tx = new PaymentTransaction("ext-2", p, q, new BigDecimal("500"), "INR");
        setId(tx, id);
        var balance = new AccountBalance(p, "INR");
        balance.applyCredit(new BigDecimal("1000"));
        when(txRepo.findById(id)).thenReturn(Optional.of(tx));
        when(balRepo.findByIdForUpdate(p)).thenReturn(Optional.of(balance));
        when(balRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(holdRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(txRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentService s = new PaymentService(txRepo, balRepo, holdRepo, ledger, idem, outbox);
        var out = s.authorize(id);

        assertEquals(TransactionStatus.AUTHORIZED, out.getStatus());
        assertEquals(new BigDecimal("500"), balance.getAvailableBalance());
        verify(holdRepo).save(any(AccountHold.class));
        verify(outbox).enqueue(eq("PAYMENT"), eq(id), eq(PaymentEventTypes.AUTHORIZED), any(), any(UUID.class));
    }

    @Test
    void settleCreatesLedgerPostingConsumesHoldAndEmitsEvent() throws Exception {
        var txRepo = mock(PaymentTransactionRepository.class);
        var balRepo = mock(AccountBalanceRepository.class);
        var holdRepo = mock(AccountHoldRepository.class);
        var ledger = mock(LedgerPostingService.class);
        var idem = mock(IdempotencyKeyRepository.class);
        var outbox = mock(OutboxEventService.class);
        UUID p = UUID.randomUUID(), q = UUID.randomUUID(), id = UUID.randomUUID(), posting = UUID.randomUUID();
        var tx = new PaymentTransaction("ext-3", p, q, new BigDecimal("500"), "INR");
        setId(tx, id);
        tx.transitionTo(TransactionStatus.AUTHORIZED);
        var hold = new AccountHold(id, p, new BigDecimal("500"), "INR");
        when(txRepo.findById(id)).thenReturn(Optional.of(tx));
        when(holdRepo.findByTransactionIdAndAccountIdAndStatus(id, p, HoldStatus.ACTIVE)).thenReturn(Optional.of(hold));
        when(ledger.post(any(LedgerPostingCommand.class))).thenReturn(posting);
        when(holdRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(txRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentService s = new PaymentService(txRepo, balRepo, holdRepo, ledger, idem, outbox);
        var out = s.settle(id);

        assertEquals(TransactionStatus.SETTLED, out.getStatus());
        verify(ledger).post(any(LedgerPostingCommand.class));
        verify(holdRepo).save(hold);
        verify(outbox).enqueue(eq("PAYMENT"), eq(id), eq(PaymentEventTypes.SETTLED), any(), any(UUID.class));
    }

    @Test
    void invalidTransitionDoesNotEmitLifecycleEvent() throws Exception {
        var txRepo = mock(PaymentTransactionRepository.class);
        var outbox = mock(OutboxEventService.class);
        UUID id = UUID.randomUUID();
        UUID p = UUID.randomUUID(), q = UUID.randomUUID();
        var tx = new PaymentTransaction("ext-4", p, q, new BigDecimal("10"), "INR");
        setId(tx, id);
        when(txRepo.findById(id)).thenReturn(Optional.of(tx));
        var s = new PaymentService(txRepo, mock(AccountBalanceRepository.class), mock(AccountHoldRepository.class),
                mock(LedgerPostingService.class), mock(IdempotencyKeyRepository.class), outbox);

        assertThrows(InvalidPaymentStateException.class, () -> s.settle(id));
        verifyNoInteractions(outbox);
    }

    private static void setId(PaymentTransaction tx, UUID id) throws Exception {
        var field = PaymentTransaction.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(tx, id);
    }
}
