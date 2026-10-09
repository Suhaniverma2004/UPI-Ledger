package com.upiledger.payments;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PaymentTransactionLifecycleGuardTest {
    @Test
    void initiatedPaymentCanBeAuthorizedOnlyOnce() {
        PaymentTransaction tx = payment();
        tx.transitionTo(TransactionStatus.AUTHORIZED);
        assertEquals(TransactionStatus.AUTHORIZED, tx.getStatus());
        assertThrows(InvalidPaymentStateException.class,
                () -> tx.transitionTo(TransactionStatus.AUTHORIZED));
    }

    @Test
    void authorizedPaymentCanBeSettledOnlyThroughAllowedTransition() {
        PaymentTransaction tx = payment();
        tx.transitionTo(TransactionStatus.AUTHORIZED);
        tx.transitionTo(TransactionStatus.SETTLED);
        assertEquals(TransactionStatus.SETTLED, tx.getStatus());
        assertThrows(InvalidPaymentStateException.class,
                () -> tx.transitionTo(TransactionStatus.SETTLED));
    }

    @Test
    void settledPaymentMustPassThroughReversalRequestedBeforeReversed() {
        PaymentTransaction tx = payment();
        tx.transitionTo(TransactionStatus.AUTHORIZED);
        tx.transitionTo(TransactionStatus.SETTLED);
        assertThrows(InvalidPaymentStateException.class,
                () -> tx.transitionTo(TransactionStatus.REVERSED));
        tx.transitionTo(TransactionStatus.REVERSAL_REQUESTED);
        tx.transitionTo(TransactionStatus.REVERSED);
        assertEquals(TransactionStatus.REVERSED, tx.getStatus());
        assertThrows(InvalidPaymentStateException.class,
                () -> tx.transitionTo(TransactionStatus.REVERSAL_REQUESTED));
    }

    @Test
    void failedAndExpiredPaymentsAreTerminal() {
        PaymentTransaction failed = payment();
        failed.transitionTo(TransactionStatus.FAILED);
        assertThrows(InvalidPaymentStateException.class,
                () -> failed.transitionTo(TransactionStatus.AUTHORIZED));

        PaymentTransaction expired = payment();
        expired.transitionTo(TransactionStatus.EXPIRED);
        assertThrows(InvalidPaymentStateException.class,
                () -> expired.transitionTo(TransactionStatus.AUTHORIZED));
    }

    @Test
    void paymentCannotSkipRequiredLifecycleStates() {
        PaymentTransaction tx = payment();
        assertThrows(InvalidPaymentStateException.class,
                () -> tx.transitionTo(TransactionStatus.SETTLED));
        assertThrows(InvalidPaymentStateException.class,
                () -> tx.transitionTo(TransactionStatus.REVERSED));
    }

    private static PaymentTransaction payment() {
        return new PaymentTransaction("v82-" + UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("125.00"), "INR");
    }
}
