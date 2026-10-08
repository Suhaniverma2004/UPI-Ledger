package com.upiledger.accounts;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface AccountHoldRepository extends JpaRepository<AccountHold, UUID> {}
