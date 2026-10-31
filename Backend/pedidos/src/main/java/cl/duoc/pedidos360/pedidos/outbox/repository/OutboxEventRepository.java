package cl.duoc.pedidos360.pedidos.outbox.repository;

import cl.duoc.pedidos360.pedidos.outbox.entity.OutboxEvent;
import cl.duoc.pedidos360.pedidos.outbox.entity.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    @Modifying
    @Query("UPDATE OutboxEvent o SET o.status = cl.duoc.pedidos360.pedidos.outbox.entity.OutboxStatus.PUBLISHED, " +
            "o.publishedAt = :now, o.lockedBy = null, o.reservationId = null, o.errorMessage = null, o.errorType = null " +
            "WHERE o.id = :id AND o.reservationId = :reservationId AND o.status = cl.duoc.pedidos360.pedidos.outbox.entity.OutboxStatus.PROCESSING")
    int markPublishedConditional(@Param("id") Long id,
                                 @Param("reservationId") String reservationId,
                                 @Param("now") Instant now);

    @Modifying
    @Query("UPDATE OutboxEvent o SET o.status = :newStatus, o.retryCount = :retryCount, o.nextRetryAt = :nextRetryAt, " +
            "o.lastAttemptedAt = :now, o.lockedBy = null, o.reservationId = null, o.errorMessage = :errorMessage, o.errorType = :errorType " +
            "WHERE o.id = :id AND o.reservationId = :reservationId AND o.status = cl.duoc.pedidos360.pedidos.outbox.entity.OutboxStatus.PROCESSING")
    int markFailedConditional(@Param("id") Long id,
                              @Param("reservationId") String reservationId,
                              @Param("newStatus") OutboxStatus newStatus,
                              @Param("retryCount") int retryCount,
                              @Param("nextRetryAt") Instant nextRetryAt,
                              @Param("errorMessage") String errorMessage,
                              @Param("errorType") String errorType,
                              @Param("now") Instant now);

    List<OutboxEvent> findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus status);

    List<OutboxEvent> findByStatus(OutboxStatus status);

    Optional<OutboxEvent> findByEventId(String eventId);

    long countByStatus(OutboxStatus status);
}
