package com.upiledger.idempotency;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Redis is an optimization layer for payment idempotency.
 * PostgreSQL remains the authoritative source of truth.
 */
@Service
public class RedisIdempotencyService {
    private static final String PREFIX = "upiledger:idempotency:";
    private static final String PROCESSING_PREFIX = "PROCESSING:";
    private static final String COMPLETED_PREFIX = "COMPLETED:";

    private final StringRedisTemplate redis;

    public RedisIdempotencyService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public Result lookup(String key, String requestHash) {
        try {
            String value = redis.opsForValue().get(PREFIX + key);
            if (value == null) {
                return Result.miss();
            }
            if (value.startsWith(COMPLETED_PREFIX)) {
                String[] parts = value.substring(COMPLETED_PREFIX.length()).split("\\|", 2);
                if (parts.length == 2 && parts[0].equals(requestHash)) {
                    return Result.completed(UUID.fromString(parts[1]));
                }
                return Result.miss();
            }
            if (value.equals(PROCESSING_PREFIX + requestHash)) {
                return Result.processing();
            }
            return Result.miss();
        } catch (RuntimeException ex) {
            // Redis must never become a financial correctness dependency.
            return Result.unavailable();
        }
    }

    public boolean tryAcquire(String key, String requestHash, Duration ttl) {
        try {
            Boolean acquired = redis.opsForValue().setIfAbsent(
                    PREFIX + key,
                    PROCESSING_PREFIX + requestHash,
                    ttl
            );
            return Boolean.TRUE.equals(acquired);
        } catch (RuntimeException ex) {
            // Fail open to PostgreSQL idempotency.
            return true;
        }
    }

    public void cacheCompleted(String key, String requestHash, UUID resourceId, Duration ttl) {
        try {
            redis.opsForValue().set(
                    PREFIX + key,
                    COMPLETED_PREFIX + requestHash + "|" + resourceId,
                    ttl
            );
        } catch (RuntimeException ignored) {
            // PostgreSQL already contains the authoritative idempotency record.
        }
    }

    public void release(String key, String requestHash) {
        try {
            String redisKey = PREFIX + key;
            String value = redis.opsForValue().get(redisKey);
            if ((PROCESSING_PREFIX + requestHash).equals(value)) {
                redis.delete(redisKey);
            }
        } catch (RuntimeException ignored) {
            // Best effort only.
        }
    }

    public record Result(Status status, UUID resourceId) {
        public enum Status { MISS, COMPLETED, PROCESSING, UNAVAILABLE }

        static Result miss() { return new Result(Status.MISS, null); }
        static Result completed(UUID id) { return new Result(Status.COMPLETED, id); }
        static Result processing() { return new Result(Status.PROCESSING, null); }
        static Result unavailable() { return new Result(Status.UNAVAILABLE, null); }
    }
}
