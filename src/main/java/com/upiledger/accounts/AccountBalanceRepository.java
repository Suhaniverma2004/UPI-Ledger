package com.upiledger.accounts;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface AccountBalanceRepository extends JpaRepository<AccountBalance, UUID> {}
