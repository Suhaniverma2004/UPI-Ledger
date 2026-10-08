package com.upiledger.payments;
import org.springframework.data.jpa.repository.*; import java.util.*;
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction,UUID>{Optional<PaymentTransaction> findByExternalTxnId(String externalTxnId);}
