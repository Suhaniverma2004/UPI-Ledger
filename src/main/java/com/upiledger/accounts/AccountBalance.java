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
}
