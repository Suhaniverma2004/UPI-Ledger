package com.upiledger.payments;
import jakarta.persistence.*;
import java.math.BigDecimal; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="transactions",uniqueConstraints=@UniqueConstraint(name="uk_transactions_external_txn_id",columnNames="external_txn_id"))
public class PaymentTransaction{
 @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
 @Column(name="external_txn_id",nullable=false,length=100) private String externalTxnId;
 @Column(name="payer_account_id",nullable=false) private UUID payerAccountId;
 @Column(name="payee_account_id",nullable=false) private UUID payeeAccountId;
 @Column(nullable=false,precision=18,scale=4) private BigDecimal amount;
 @Column(nullable=false,length=3) private String currency;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private TransactionStatus status;
 @Version @Column(nullable=false) private long version;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 protected PaymentTransaction(){}
 public PaymentTransaction(String externalTxnId,UUID payerAccountId,UUID payeeAccountId,BigDecimal amount,String currency){this.externalTxnId=externalTxnId;this.payerAccountId=payerAccountId;this.payeeAccountId=payeeAccountId;this.amount=amount;this.currency=currency;this.status=TransactionStatus.INITIATED;this.createdAt=Instant.now();this.updatedAt=this.createdAt;}
 public UUID getId(){return id;} public String getExternalTxnId(){return externalTxnId;} public UUID getPayerAccountId(){return payerAccountId;} public UUID getPayeeAccountId(){return payeeAccountId;} public BigDecimal getAmount(){return amount;} public String getCurrency(){return currency;} public TransactionStatus getStatus(){return status;}
 public void transitionTo(TransactionStatus next){ if(!isAllowed(status,next)) throw new InvalidPaymentStateException(status,next); status=next; updatedAt=Instant.now(); }
 private boolean isAllowed(TransactionStatus from,TransactionStatus to){return switch(from){case INITIATED->to==TransactionStatus.AUTHORIZED||to==TransactionStatus.FAILED||to==TransactionStatus.EXPIRED;case AUTHORIZED->to==TransactionStatus.SETTLED||to==TransactionStatus.FAILED;case SETTLED->to==TransactionStatus.REVERSAL_REQUESTED;case REVERSAL_REQUESTED->to==TransactionStatus.REVERSED;case FAILED,EXPIRED,REVERSED->false;};}
}
