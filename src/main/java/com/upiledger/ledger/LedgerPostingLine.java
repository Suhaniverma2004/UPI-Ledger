package com.upiledger.ledger;

import java.math.BigDecimal;
import java.util.UUID;

public record LedgerPostingLine(UUID accountId, LedgerEntryType entryType, BigDecimal amount) {
    public LedgerPostingLine {
        if (accountId == null || entryType == null || amount == null) {
            throw new IllegalArgumentException("Posting line fields are required");
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Posting amount must be positive");
        }
    }
}
