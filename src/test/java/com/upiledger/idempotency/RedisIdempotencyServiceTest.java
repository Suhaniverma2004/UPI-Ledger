package com.upiledger.idempotency;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisIdempotencyServiceTest {

    @Test
    void cacheHitReturnsCompletedResource() {
        var redis = mock(StringRedisTemplate.class);
        var values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);

        UUID id = UUID.randomUUID();
        when(values.get("upiledger:idempotency:key-1"))
                .thenReturn("COMPLETED:hash-1|" + id);

        var service = new RedisIdempotencyService(redis);

        var result = service.lookup("key-1", "hash-1");

        assertEquals(RedisIdempotencyService.Result.Status.COMPLETED, result.status());
        assertEquals(id, result.resourceId());
    }

    @Test
    void acquireUsesSetIfAbsentWithTtl() {
        var redis = mock(StringRedisTemplate.class);
        var values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(eq("upiledger:idempotency:key-2"),
                eq("PROCESSING:hash-2"), any(Duration.class))).thenReturn(true);

        var service = new RedisIdempotencyService(redis);

        assertTrue(service.tryAcquire("key-2", "hash-2", Duration.ofSeconds(30)));
        verify(values).setIfAbsent(eq("upiledger:idempotency:key-2"),
                eq("PROCESSING:hash-2"), eq(Duration.ofSeconds(30)));
    }

    @Test
    void redisFailureFailsOpenForAcquire() {
        var redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new RuntimeException("redis unavailable"));

        var service = new RedisIdempotencyService(redis);

        assertTrue(service.tryAcquire("key-3", "hash-3", Duration.ofSeconds(30)));
        assertEquals(RedisIdempotencyService.Result.Status.UNAVAILABLE,
                service.lookup("key-3", "hash-3").status());
    }

    @Test
    void completedCacheStoresHashAndResource() {
        var redis = mock(StringRedisTemplate.class);
        var values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        UUID id = UUID.randomUUID();

        var service = new RedisIdempotencyService(redis);
        service.cacheCompleted("key-4", "hash-4", id, Duration.ofHours(24));

        verify(values).set(eq("upiledger:idempotency:key-4"),
                eq("COMPLETED:hash-4|" + id), eq(Duration.ofHours(24)));
    }
}
