package com.upiledger.idempotency;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {}
