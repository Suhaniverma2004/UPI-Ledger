package com.upiledger.eventing;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OutboxPublisher {

    private final OutboxEventRepository repository;
    private final OutboxPublishWorker worker;

    public OutboxPublisher(
            OutboxEventRepository repository,
            OutboxPublishWorker worker
    ) {
        this.repository = repository;
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "${upiledger.outbox.publisher.fixed-delay-ms:1000}")
    public void publishPendingEvents() {
        var events = repository.findNextClaimable(
                List.of(OutboxStatus.PENDING, OutboxStatus.FAILED),
                PageRequest.of(0, 100)
        );

        for (OutboxEvent event : events) {
            worker.claimAndPublish(event);
        }
    }
}
