package com.upiledger.accounts;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface AccountBalanceRepository extends JpaRepository<AccountBalance, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from AccountBalance b where b.accountId = :accountId")
    Optional<AccountBalance> findByIdForUpdate(UUID accountId);
}
