package com.upiledger.idempotency;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisIdempotencyConcurrencyTest {

    @Test
    void concurrentAcquireAttemptsUseAtomicSetIfAbsent() throws Exception {
        var redis = mock(StringRedisTemplate.class);
        var values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);

        AtomicInteger calls = new AtomicInteger();
        when(values.setIfAbsent(
                eq("upiledger:idempotency:race-key"),
                eq("PROCESSING:race-hash"),
                eq(Duration.ofSeconds(30))))
                .thenAnswer(invocation -> calls.getAndIncrement() == 0);

        var service = new RedisIdempotencyService(redis);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<Boolean> first = executor.submit(() -> acquireAfter(start, service));
            Future<Boolean> second = executor.submit(() -> acquireAfter(start, service));
            start.countDown();

            boolean a = first.get(5, TimeUnit.SECONDS);
            boolean b = second.get(5, TimeUnit.SECONDS);

            assertEquals(1, (a ? 1 : 0) + (b ? 1 : 0));
            verify(values, times(2)).setIfAbsent(
                    eq("upiledger:idempotency:race-key"),
                    eq("PROCESSING:race-hash"),
                    eq(Duration.ofSeconds(30)));
        } finally {
            executor.shutdownNow();
        }
    }

    private static boolean acquireAfter(
            CountDownLatch start,
            RedisIdempotencyService service) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        return service.tryAcquire("race-key", "race-hash", Duration.ofSeconds(30));
    }
}
