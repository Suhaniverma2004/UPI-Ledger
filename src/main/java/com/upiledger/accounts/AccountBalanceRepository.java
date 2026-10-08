package com.upiledger.accounts;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface AccountBalanceRepository extends JpaRepository<AccountBalance,UUID>{
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select b from AccountBalance b where b.accountId = :accountId") Optional<AccountBalance> findByIdForUpdate(UUID accountId);
}
