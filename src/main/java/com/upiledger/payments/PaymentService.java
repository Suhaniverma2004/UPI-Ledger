package com.upiledger.payments;

import com.upiledger.accounts.*;
import com.upiledger.eventing.OutboxEventService;
import com.upiledger.idempotency.*;
import com.upiledger.ledger.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.UUID;

@Service
public class PaymentService {
    private final PaymentTransactionRepository transactions;
    private final AccountBalanceRepository balances;
    private final AccountHoldRepository holds;
    private final LedgerPostingService ledger;
    private final IdempotencyKeyRepository idempotency;
    private final OutboxEventService outbox;

    public PaymentService(PaymentTransactionRepository transactions,
                          AccountBalanceRepository balances,
                          AccountHoldRepository holds,
                          LedgerPostingService ledger,
                          IdempotencyKeyRepository idempotency,
                          OutboxEventService outbox) {
        this.transactions = transactions;
        this.balances = balances;
        this.holds = holds;
        this.ledger = ledger;
        this.idempotency = idempotency;
        this.outbox = outbox;
    }

    @Transactional
    public PaymentTransaction create(CreatePaymentCommand c, String idempotencyKey) {
        String hash = hash(c);
        var existing = idempotency.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().getRequestHash().equals(hash)) throw new IdempotencyConflictException();
            if (existing.get().getResourceId() != null) {
                return transactions.findById(existing.get().getResourceId()).orElseThrow();
            }
        }

        var tx = transactions.findByExternalTxnId(c.externalTxnId()).orElse(null);
        if (tx != null) {
            if (!same(tx, c)) throw new IllegalArgumentException("external_txn_id already belongs to a different payment");
            return tx;
        }

        tx = transactions.save(new PaymentTransaction(
                c.externalTxnId(), c.payerAccountId(), c.payeeAccountId(), c.amount(), c.currency()));
        try {
            IdempotencyKey key = existing.orElseGet(() -> idempotency.save(
                    new IdempotencyKey(idempotencyKey, hash, Instant.now().plus(24, ChronoUnit.HOURS))));
            key.complete(tx.getId(), 201, "{\"transactionId\":\"" + tx.getId() + "\"}");
            idempotency.save(key);
        } catch (DataIntegrityViolationException e) {
            throw new IdempotencyConflictException();
        }

        emit(tx, PaymentEventTypes.INITIATED);
        return tx;
    }

    @Transactional
    public PaymentTransaction authorize(UUID id) {
        PaymentTransaction tx = get(id);
        if (tx.getStatus() != TransactionStatus.INITIATED) {
            throw new InvalidPaymentStateException(tx.getStatus(), TransactionStatus.AUTHORIZED);
        }
        AccountBalance b = lock(tx.getPayerAccountId());
        if (!b.getCurrency().equals(tx.getCurrency())) throw new IllegalArgumentException("Currency mismatch");
        b.reserve(tx.getAmount());
        balances.save(b);
        holds.save(new AccountHold(tx.getId(), tx.getPayerAccountId(), tx.getAmount(), tx.getCurrency()));
        tx.transitionTo(TransactionStatus.AUTHORIZED);
        tx = transactions.save(tx);
        emit(tx, PaymentEventTypes.AUTHORIZED);
        return tx;
    }

    @Transactional
    public PaymentTransaction settle(UUID id) {
        PaymentTransaction tx = get(id);
        if (tx.getStatus() != TransactionStatus.AUTHORIZED) {
            throw new InvalidPaymentStateException(tx.getStatus(), TransactionStatus.SETTLED);
        }
        AccountHold hold = holds.findByTransactionIdAndAccountIdAndStatus(
                tx.getId(), tx.getPayerAccountId(), HoldStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("Active authorization hold not found"));

        UUID posting = ledger.post(new LedgerPostingCommand(
                tx.getId(), tx.getCurrency(), LedgerEntryReason.SETTLEMENT,
                java.util.List.of(
                        new LedgerPostingLine(tx.getPayerAccountId(), LedgerEntryType.DEBIT, tx.getAmount()),
                        new LedgerPostingLine(tx.getPayeeAccountId(), LedgerEntryType.CREDIT, tx.getAmount())
                )));

        hold.consume(posting);
        holds.save(hold);
        tx.transitionTo(TransactionStatus.SETTLED);
        tx = transactions.save(tx);
        emit(tx, PaymentEventTypes.SETTLED);
        return tx;
    }

    @Transactional
    public PaymentTransaction fail(UUID id) {
        PaymentTransaction tx = get(id);
        if (tx.getStatus() == TransactionStatus.AUTHORIZED) releaseHold(tx);
        tx.transitionTo(TransactionStatus.FAILED);
        tx = transactions.save(tx);
        emit(tx, PaymentEventTypes.FAILED);
        return tx;
    }

    @Transactional
    public PaymentTransaction expire(UUID id) {
        PaymentTransaction tx = get(id);
        if (tx.getStatus() == TransactionStatus.AUTHORIZED) releaseHold(tx);
        tx.transitionTo(TransactionStatus.EXPIRED);
        tx = transactions.save(tx);
        emit(tx, PaymentEventTypes.EXPIRED);
        return tx;
    }

    @Transactional
    public PaymentTransaction reverse(UUID id) {
        PaymentTransaction tx = get(id);
        if (tx.getStatus() != TransactionStatus.SETTLED) {
            throw new InvalidPaymentStateException(tx.getStatus(), TransactionStatus.REVERSAL_REQUESTED);
        }

        tx.transitionTo(TransactionStatus.REVERSAL_REQUESTED);
        tx = transactions.save(tx);
        emit(tx, PaymentEventTypes.REVERSAL_REQUESTED);

        ledger.post(new LedgerPostingCommand(
                tx.getId(), tx.getCurrency(), LedgerEntryReason.REVERSAL,
                java.util.List.of(
                        new LedgerPostingLine(tx.getPayerAccountId(), LedgerEntryType.CREDIT, tx.getAmount()),
                        new LedgerPostingLine(tx.getPayeeAccountId(), LedgerEntryType.DEBIT, tx.getAmount())
                )));

        tx.transitionTo(TransactionStatus.REVERSED);
        tx = transactions.save(tx);
        emit(tx, PaymentEventTypes.REVERSED);
        return tx;
    }

    private void emit(PaymentTransaction tx, String eventType) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("transactionId", tx.getId());
        payload.put("externalTxnId", tx.getExternalTxnId());
        payload.put("payerAccountId", tx.getPayerAccountId());
        payload.put("payeeAccountId", tx.getPayeeAccountId());
        payload.put("amount", tx.getAmount());
        payload.put("currency", tx.getCurrency());
        payload.put("status", tx.getStatus().name());
        outbox.enqueue("PAYMENT", tx.getId(), eventType, payload, UUID.randomUUID());
    }

    private void releaseHold(PaymentTransaction tx) {
        AccountHold h = holds.findByTransactionIdAndAccountIdAndStatus(
                tx.getId(), tx.getPayerAccountId(), HoldStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("Active authorization hold not found"));
        AccountBalance b = lock(tx.getPayerAccountId());
        b.release(h.getAmount());
        balances.save(b);
        h.release();
        holds.save(h);
    }

    private AccountBalance lock(UUID id) {
        return balances.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Account balance not found: " + id));
    }

    public PaymentTransaction get(UUID id) {
        return transactions.findById(id).orElseThrow(() -> new IllegalArgumentException("Payment not found: " + id));
    }

    private boolean same(PaymentTransaction tx, CreatePaymentCommand c) {
        return tx.getPayerAccountId().equals(c.payerAccountId())
                && tx.getPayeeAccountId().equals(c.payeeAccountId())
                && tx.getAmount().compareTo(c.amount()) == 0
                && tx.getCurrency().equals(c.currency());
    }

    private String hash(CreatePaymentCommand c) {
        String v = c.externalTxnId() + "|" + c.payerAccountId() + "|" + c.payeeAccountId() + "|"
                + c.amount().stripTrailingZeros().toPlainString() + "|" + c.currency();
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte x : d) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public record CreatePaymentCommand(String externalTxnId, UUID payerAccountId, UUID payeeAccountId,
                                       BigDecimal amount, String currency) {
        public CreatePaymentCommand {
            if (externalTxnId == null || externalTxnId.isBlank() || payerAccountId == null || payeeAccountId == null
                    || payerAccountId.equals(payeeAccountId) || amount == null || amount.signum() <= 0
                    || currency == null || !currency.matches("[A-Z]{3}")) {
                throw new IllegalArgumentException("Invalid payment request");
            }
        }
    }
}
