package com.upiledger.payments;
public class InvalidPaymentStateException extends RuntimeException{public InvalidPaymentStateException(TransactionStatus from,TransactionStatus to){super("Invalid payment transition: "+from+" -> "+to);}}
