package com.upiledger.payments;

/** Stable event names used by the payment lifecycle. */
public final class PaymentEventTypes {
    private PaymentEventTypes() { }

    public static final String INITIATED = "PAYMENT_INITIATED";
    public static final String AUTHORIZED = "PAYMENT_AUTHORIZED";
    public static final String SETTLED = "PAYMENT_SETTLED";
    public static final String FAILED = "PAYMENT_FAILED";
    public static final String EXPIRED = "PAYMENT_EXPIRED";
    public static final String REVERSAL_REQUESTED = "PAYMENT_REVERSAL_REQUESTED";
    public static final String REVERSED = "PAYMENT_REVERSED";
}
