package com.upiledger.eventing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KafkaTopicsTest {
    private final KafkaTopics topics = new KafkaTopics(
            "lifecycle", "ledger", "settled", "failed", "reversed", "dlq");

    @Test
    void routesLifecycleEventsToLifecycleTopic() {
        assertEquals("lifecycle", topics.topicFor("PAYMENT_INITIATED"));
        assertEquals("lifecycle", topics.topicFor("PAYMENT_AUTHORIZED"));
        assertEquals("lifecycle", topics.topicFor("PAYMENT_REVERSAL_REQUESTED"));
    }

    @Test
    void routesSpecializedPaymentEventsToDedicatedTopics() {
        assertEquals("ledger", topics.topicFor("LEDGER_ENTRY_POSTED"));
        assertEquals("settled", topics.topicFor("PAYMENT_SETTLED"));
        assertEquals("failed", topics.topicFor("PAYMENT_FAILED"));
        assertEquals("failed", topics.topicFor("PAYMENT_EXPIRED"));
        assertEquals("reversed", topics.topicFor("PAYMENT_REVERSED"));
    }
}
