package com.upiledger.payments;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {}
