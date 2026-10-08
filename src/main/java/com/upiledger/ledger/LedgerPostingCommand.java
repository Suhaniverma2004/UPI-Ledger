package com.upiledger.ledger;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record LedgerPostingCommand(UUID transactionId, String currency, LedgerEntryReason reason,
                                   List<LedgerPostingLine> lines) {
    public LedgerPostingCommand {
        if (transactionId == null || currency == null || reason == null || lines == null) {
            throw new IllegalArgumentException("Posting command fields are required");
        }
        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Currency must be a three-letter uppercase ISO code");
        }
        if (lines.size() < 2) {
            throw new IllegalArgumentException("A posting requires at least two ledger lines");
        }
        lines = List.copyOf(lines);
    }

    public BigDecimal debitTotal() {
        return lines.stream().filter(l -> l.entryType() == LedgerEntryType.DEBIT)
                .map(LedgerPostingLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal creditTotal() {
        return lines.stream().filter(l -> l.entryType() == LedgerEntryType.CREDIT)
                .map(LedgerPostingLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
