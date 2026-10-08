package com.upiledger.eventing;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OutboxPublisherTest {

    @Test
    void delegatesClaimableEventsToWorker() {
        var repository = mock(OutboxEventRepository.class);
        var worker = mock(OutboxPublishWorker.class);
        var publisher = new OutboxPublisher(repository, worker);

        var event = mock(OutboxEvent.class);
        when(repository.findNextClaimable(
                eq(List.of(OutboxStatus.PENDING, OutboxStatus.FAILED)),
                any(Pageable.class)
        )).thenReturn(List.of(event));

        publisher.publishPendingEvents();

        verify(worker).claimAndPublish(event);
    }

    @Test
    void doesNothingWhenNoClaimableEventsExist() {
        var repository = mock(OutboxEventRepository.class);
        var worker = mock(OutboxPublishWorker.class);
        var publisher = new OutboxPublisher(repository, worker);

        when(repository.findNextClaimable(
                eq(List.of(OutboxStatus.PENDING, OutboxStatus.FAILED)),
                any(Pageable.class)
        )).thenReturn(List.of());

        publisher.publishPendingEvents();

        verifyNoInteractions(worker);
    }
}
