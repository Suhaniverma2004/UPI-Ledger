package com.upiledger.eventing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
public class OutboxPublisher {
    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxEventService outboxEventService;
    private final KafkaTopics topics;

    @Value("${upiledger.outbox.publisher.retry-delay-ms:5000}")
    private long retryDelayMs;

    public OutboxPublisher(OutboxEventRepository repository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           OutboxEventService outboxEventService,
                           KafkaTopics topics) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.outboxEventService = outboxEventService;
        this.topics = topics;
    }

    @Scheduled(fixedDelayString = "${upiledger.outbox.publisher.fixed-delay-ms:1000}")
    public void publishPendingEvents() {
        List<OutboxEvent> events = repository.findTop100ByStatusInOrderByCreatedAtAsc(
                List.of(OutboxStatus.PENDING, OutboxStatus.FAILED));

        Instant now = Instant.now();
        Duration retryDelay = Duration.ofMillis(retryDelayMs);

        for (OutboxEvent event : events) {
            if (event.isRetryEligible(now, retryDelay)) {
                publishOne(event);
            }
        }
    }

    @Transactional
    public void publishOne(OutboxEvent event) {
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
        } catch (Exception e) {
            event.markFailed(Instant.now(), rootMessage(e));
            repository.save(event);
        }
    }

    private String rootMessage(Exception exception) {
        Throwable current = exception;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
