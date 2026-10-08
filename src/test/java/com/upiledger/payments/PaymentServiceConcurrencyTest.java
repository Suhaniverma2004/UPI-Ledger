package com.upiledger.payments;

import com.upiledger.accounts.AccountBalanceRepository;
import com.upiledger.accounts.AccountHoldRepository;
import com.upiledger.eventing.OutboxEventService;
import com.upiledger.idempotency.IdempotencyKey;
import com.upiledger.idempotency.IdempotencyKeyRepository;
import com.upiledger.idempotency.IdempotencyInProgressException;
import com.upiledger.idempotency.RedisIdempotencyService;
import com.upiledger.ledger.LedgerPostingService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentServiceConcurrencyTest {

    @Test
    void concurrentSameIdempotencyKeyAllowsOnlyOneRedisOwner() throws Exception {
        var txRepo = mock(PaymentTransactionRepository.class);
        var balances = mock(AccountBalanceRepository.class);
        var holds = mock(AccountHoldRepository.class);
        var ledger = mock(LedgerPostingService.class);
        var idempotency = mock(IdempotencyKeyRepository.class);
        var outbox = mock(OutboxEventService.class);
        var redis = new AtomicRedisGate();

        UUID payer = UUID.randomUUID();
        UUID payee = UUID.randomUUID();
        var command = new PaymentService.CreatePaymentCommand(
                "ext-concurrent-1", payer, payee, new BigDecimal("500"), "INR");

        when(idempotency.findByIdempotencyKey("same-key")).thenReturn(Optional.empty());
        when(txRepo.findByExternalTxnId("ext-concurrent-1")).thenReturn(Optional.empty());

        AtomicInteger saves = new AtomicInteger();
        when(txRepo.save(any(PaymentTransaction.class))).thenAnswer(invocation -> {
            saves.incrementAndGet();
            PaymentTransaction tx = invocation.getArgument(0);
            setId(tx, UUID.randomUUID());
            return tx;
        });
        when(idempotency.save(any(IdempotencyKey.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var service = new PaymentService(txRepo, balances, holds, ledger, idempotency, outbox, redis);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<PaymentTransaction> first = executor.submit(() -> createAfter(start, service, command));
            Future<PaymentTransaction> second = executor.submit(() -> createAfter(start, service, command));
            start.countDown();

            int successes = 0;
            int inProgress = 0;
            for (Future<PaymentTransaction> future : java.util.List.of(first, second)) {
                try {
                    assertNotNull(future.get(5, TimeUnit.SECONDS));
                    successes++;
                } catch (ExecutionException ex) {
                    assertInstanceOf(IdempotencyInProgressException.class, ex.getCause());
                    inProgress++;
                }
            }

            assertEquals(1, successes);
            assertEquals(1, inProgress);
            assertEquals(1, saves.get(), "Only the Redis owner may create the payment");
            assertEquals(1, redis.acquireCount.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private static PaymentTransaction createAfter(
            CountDownLatch start,
            PaymentService service,
            PaymentService.CreatePaymentCommand command) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        return service.create(command, "same-key");
    }

    private static void setId(PaymentTransaction tx, UUID id) {
        try {
            Field field = PaymentTransaction.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(tx, id);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Unable to assign test transaction id", ex);
        }
    }

    /**
     * Deterministic test double for the Redis SET NX coordination primitive.
     * The production implementation uses Redis' atomic setIfAbsent operation.
     */
    private static final class AtomicRedisGate extends RedisIdempotencyService {
        private final AtomicBoolean owner = new AtomicBoolean();
        private final AtomicInteger acquireCount = new AtomicInteger();
        private volatile String hash;
        private volatile UUID resourceId;

        private AtomicRedisGate() {
            super(null);
        }

        @Override
        public Result lookup(String key, String requestHash) {
            if (resourceId != null && hash != null && hash.equals(requestHash)) {
                return new Result(RedisIdempotencyService.Result.Status.COMPLETED, resourceId);
            }
            if (owner.get() && hash != null && hash.equals(requestHash)) {
                return new Result(RedisIdempotencyService.Result.Status.PROCESSING, null);
            }
            return new Result(RedisIdempotencyService.Result.Status.MISS, null);
        }

        @Override
        public boolean tryAcquire(String key, String requestHash, Duration ttl) {
            if (owner.compareAndSet(false, true)) {
                hash = requestHash;
                acquireCount.incrementAndGet();
                return true;
            }
            return false;
        }

        @Override
        public void cacheCompleted(String key, String requestHash, UUID resourceId, Duration ttl) {
            this.resourceId = resourceId;
            this.hash = requestHash;
        }
    }
}
