package com.upiledger.eventing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class OutboxPublishWorker {

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxEventService outboxEventService;
    private final KafkaTopics topics;

    @Value("${upiledger.outbox.publisher.retry-delay-ms:5000}")
    private long retryDelayMs;

    @Value("${upiledger.outbox.publisher.max-attempts:5}")
    private int maxAttempts;

    public OutboxPublishWorker(
            OutboxEventRepository repository,
            KafkaTemplate<String, String> kafkaTemplate,
            OutboxEventService outboxEventService,
            KafkaTopics topics
    ) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.outboxEventService = outboxEventService;
        this.topics = topics;
    }

    @Transactional
    public boolean claimAndPublish(OutboxEvent event) {
        Instant now = Instant.now();

        if (!event.isRetryEligible(
                now,
                Duration.ofMillis(retryDelayMs),
                maxAttempts
        )) {
            return false;
        }

        event.markProcessing(now);
        repository.saveAndFlush(event);

        try {
            var envelope = new EventEnvelope(
                    event.getId(),
                    event.getEventType(),
                    event.getAggregateType(),
                    event.getAggregateId(),
                    event.getCreatedAt(),
                    event.getCorrelationId(),
                    event.getPayload()
            );

            kafkaTemplate.send(
                    topics.topicFor(event.getEventType()),
                    event.getAggregateId().toString(),
                    outboxEventService.serialize(envelope)
            ).get();

            event.markPublished(Instant.now());
            repository.save(event);
            return true;
        } catch (Exception e) {
            event.releaseForRetry(Instant.now(), rootMessage(e));
            repository.save(event);
            return false;
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }
}
