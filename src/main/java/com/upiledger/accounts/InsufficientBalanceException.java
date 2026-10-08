package com.upiledger.accounts;

import java.math.BigDecimal;
import java.util.UUID;

public class InsufficientBalanceException extends RuntimeException {
    public InsufficientBalanceException(UUID accountId, BigDecimal amount) {
        super("Insufficient available balance for account " + accountId + " for amount " + amount);
    }
}
