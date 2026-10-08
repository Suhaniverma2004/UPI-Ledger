package com.upiledger.idempotency; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey,UUID>{Optional<IdempotencyKey> findByIdempotencyKey(String key);}
