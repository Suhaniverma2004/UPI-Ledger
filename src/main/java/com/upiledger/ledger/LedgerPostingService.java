package com.upiledger.ledger;

import com.upiledger.accounts.AccountBalance;
import com.upiledger.accounts.AccountBalanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.upiledger.eventing.OutboxEventService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LedgerPostingService {
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountBalanceRepository accountBalanceRepository;
    private final OutboxEventService outboxEventService;

    public LedgerPostingService(LedgerEntryRepository ledgerEntryRepository,
                                AccountBalanceRepository accountBalanceRepository,
                                OutboxEventService outboxEventService) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.accountBalanceRepository = accountBalanceRepository;
        this.outboxEventService = outboxEventService;
    }

    @Transactional
    public UUID post(LedgerPostingCommand command) {
        validateBalanced(command);

        UUID postingId = UUID.randomUUID();
        List<LedgerPostingLine> lines = command.lines();

        // Lock account balances in deterministic UUID order to reduce deadlock risk.
        Map<UUID, AccountBalance> balances = lines.stream()
                .map(LedgerPostingLine::accountId)
                .distinct()
                .sorted()
                .map(accountId -> accountBalanceRepository.findByIdForUpdate(accountId)
                        .orElseThrow(() -> new IllegalArgumentException("Account balance not found: " + accountId)))
                .collect(Collectors.toMap(AccountBalance::getAccountId, Function.identity()));

        for (LedgerPostingLine line : lines) {
            AccountBalance balance = balances.get(line.accountId());
            if (line.entryType() == LedgerEntryType.DEBIT) {
                if (command.reason() == LedgerEntryReason.SETTLEMENT && line.entryType() == LedgerEntryType.DEBIT) {
                    balance.applySettlementDebit(line.amount());
                } else {
                    balance.applyDebit(line.amount());
                }
            } else {
                balance.applyCredit(line.amount());
            }
            accountBalanceRepository.save(balance);

            ledgerEntryRepository.save(new LedgerEntry(
                    postingId, command.transactionId(), line.accountId(), command.currency(),
                    line.entryType(), line.amount(), command.reason()));
        }

        outboxEventService.enqueue(
                "LEDGER_POSTING",
                command.transactionId(),
                "LEDGER_ENTRY_POSTED",
                Map.of(
                        "postingId", postingId,
                        "transactionId", command.transactionId(),
                        "currency", command.currency(),
                        "reason", command.reason().name(),
                        "lines", lines
                ),
                UUID.randomUUID()
        );

        return postingId;
    }

    private void validateBalanced(LedgerPostingCommand command) {
        BigDecimal debit = command.debitTotal();
        BigDecimal credit = command.creditTotal();
        if (debit.signum() == 0 || credit.signum() == 0 || debit.compareTo(credit) != 0) {
            throw new UnbalancedPostingException(debit, credit);
        }
    }
}
