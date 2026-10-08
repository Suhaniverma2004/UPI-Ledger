package com.upiledger.payments;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions", uniqueConstraints = @UniqueConstraint(
    name = "uk_transactions_external_txn_id", columnNames = "external_txn_id"))
public class PaymentTransaction {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "external_txn_id", nullable = false, length = 100)
    private String externalTxnId;

    @Column(name = "payer_account_id", nullable = false)
    private UUID payerAccountId;

    @Column(name = "payee_account_id", nullable = false)
    private UUID payeeAccountId;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransactionStatus status;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentTransaction() {}
}
