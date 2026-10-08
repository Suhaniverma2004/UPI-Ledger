package com.upiledger.accounts;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_balances")
public class AccountBalance {
    @Id
    @Column(name = "account_id")
    private UUID accountId;

    @Column(name = "committed_balance", nullable = false, precision = 18, scale = 4)
    private BigDecimal committedBalance;

    @Column(name = "available_balance", nullable = false, precision = 18, scale = 4)
    private BigDecimal availableBalance;

    @Column(nullable = false, length = 3)
    private String currency;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AccountBalance() {}

    public AccountBalance(UUID accountId, String currency) {
        this.accountId = accountId;
        this.currency = currency;
        this.committedBalance = BigDecimal.ZERO;
        this.availableBalance = BigDecimal.ZERO;
        this.updatedAt = Instant.now();
    }

    public UUID getAccountId() { return accountId; }
    public BigDecimal getCommittedBalance() { return committedBalance; }
    public BigDecimal getAvailableBalance() { return availableBalance; }
    public String getCurrency() { return currency; }
    public long getVersion() { return version; }

    public void applyDebit(BigDecimal amount) {
        BigDecimal newCommitted = committedBalance.subtract(amount);
        BigDecimal newAvailable = availableBalance.subtract(amount);
        if (newCommitted.signum() < 0 || newAvailable.signum() < 0) {
            throw new InsufficientBalanceException(accountId, amount);
        }
        committedBalance = newCommitted;
        availableBalance = newAvailable;
        updatedAt = Instant.now();
    }

    public void applyCredit(BigDecimal amount) {
        committedBalance = committedBalance.add(amount);
        availableBalance = availableBalance.add(amount);
        updatedAt = Instant.now();
    }
}
