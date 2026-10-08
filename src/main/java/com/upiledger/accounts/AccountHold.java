package com.upiledger.accounts;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="account_holds", indexes={@Index(name="idx_holds_transaction",columnList="transaction_id"),@Index(name="idx_holds_account_status",columnList="account_id,status")})
public class AccountHold {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="transaction_id",nullable=false) private UUID transactionId;
    @Column(name="account_id",nullable=false) private UUID accountId;
    @Column(nullable=false,precision=18,scale=4) private BigDecimal amount;
    @Column(nullable=false,length=3) private String currency;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private HoldStatus status;
    @Column(name="settlement_ref") private UUID settlementRef;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    protected AccountHold() {}
    public AccountHold(UUID transactionId,UUID accountId,BigDecimal amount,String currency){this.transactionId=transactionId;this.accountId=accountId;this.amount=amount;this.currency=currency;this.status=HoldStatus.ACTIVE;this.createdAt=Instant.now();this.updatedAt=this.createdAt;}
    public UUID getId(){return id;} public UUID getTransactionId(){return transactionId;} public UUID getAccountId(){return accountId;} public BigDecimal getAmount(){return amount;} public String getCurrency(){return currency;} public HoldStatus getStatus(){return status;} public UUID getSettlementRef(){return settlementRef;}
    public void consume(UUID ref){if(status!=HoldStatus.ACTIVE) throw new IllegalStateException("Hold is not active"); status=HoldStatus.CONSUMED; settlementRef=ref; updatedAt=Instant.now();}
    public void release(){if(status!=HoldStatus.ACTIVE) throw new IllegalStateException("Hold is not active"); status=HoldStatus.RELEASED; updatedAt=Instant.now();}
}
