package cl.duoc.pedidos360.pedidos.outbox.repository;

import cl.duoc.pedidos360.pedidos.outbox.entity.OutboxEvent;
import cl.duoc.pedidos360.pedidos.outbox.entity.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Query(value = "SELECT * FROM outbox_event " +
            "WHERE (status = 'PENDING' AND (next_retry_at IS NULL OR next_retry_at <= :now)) " +
            "   OR (status = 'PROCESSING' AND processing_started_at <= :stuckThreshold) " +
            "ORDER BY created_at ASC LIMIT :batchSize " +
            "FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEvent> findAndLockBatchForProcessing(@Param("now") Instant now,
                                                    @Param("stuckThreshold") Instant stuckThreshold,
                                                    @Param("batchSize") int batchSize);

    List<OutboxEvent> findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus status);

    List<OutboxEvent> findByStatus(OutboxStatus status);

    Optional<OutboxEvent> findByEventId(String eventId);

    long countByStatus(OutboxStatus status);
}
