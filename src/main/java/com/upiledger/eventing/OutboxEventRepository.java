package com.upiledger.eventing;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findTop100ByStatusInOrderByCreatedAtAsc(Collection<OutboxStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM OutboxEvent e " +
           "WHERE e.status IN :statuses " +
           "ORDER BY e.createdAt ASC")
    List<OutboxEvent> findNextClaimable(
            @Param("statuses") List<OutboxStatus> statuses,
            Pageable pageable
    );

}
