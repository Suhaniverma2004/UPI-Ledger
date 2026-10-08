package com.upiledger.eventing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class KafkaTopics {
    private final String paymentLifecycle;
    private final String ledgerEntryPosted;
    private final String paymentSettled;
    private final String paymentFailed;
    private final String paymentReversed;
    private final String deadLetter;

    public KafkaTopics(
            @Value("${upiledger.kafka.topics.payment-lifecycle:upiledger.payment.lifecycle.v1}") String paymentLifecycle,
            @Value("${upiledger.kafka.topics.ledger-entry-posted:upiledger.ledger.entry.posted.v1}") String ledgerEntryPosted,
            @Value("${upiledger.kafka.topics.payment-settled:upiledger.payment.settled.v1}") String paymentSettled,
            @Value("${upiledger.kafka.topics.payment-failed:upiledger.payment.failed.v1}") String paymentFailed,
            @Value("${upiledger.kafka.topics.payment-reversed:upiledger.payment.reversed.v1}") String paymentReversed,
            @Value("${upiledger.kafka.topics.dlq:upiledger.dlq.v1}") String deadLetter) {
        this.paymentLifecycle = paymentLifecycle;
        this.ledgerEntryPosted = ledgerEntryPosted;
        this.paymentSettled = paymentSettled;
        this.paymentFailed = paymentFailed;
        this.paymentReversed = paymentReversed;
        this.deadLetter = deadLetter;
    }

    public String topicFor(String eventType) {
        return switch (eventType) {
            case "LEDGER_ENTRY_POSTED" -> ledgerEntryPosted;
            case "PAYMENT_SETTLED" -> paymentSettled;
            case "PAYMENT_FAILED", "PAYMENT_EXPIRED" -> paymentFailed;
            case "PAYMENT_REVERSED" -> paymentReversed;
            default -> paymentLifecycle;
        };
    }

    public String paymentLifecycle() { return paymentLifecycle; }
    public String ledgerEntryPosted() { return ledgerEntryPosted; }
    public String paymentSettled() { return paymentSettled; }
    public String paymentFailed() { return paymentFailed; }
    public String paymentReversed() { return paymentReversed; }
    public String deadLetter() { return deadLetter; }
}
