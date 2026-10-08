package com.upiledger.accounts;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface AccountHoldRepository extends JpaRepository<AccountHold,UUID> { Optional<AccountHold> findByTransactionIdAndAccountIdAndStatus(UUID transactionId, UUID accountId, HoldStatus status); }
