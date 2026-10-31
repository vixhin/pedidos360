package cl.duoc.pedidos360.analitica.repository;

import cl.duoc.pedidos360.analitica.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

    boolean existsByEventId(String eventId);

    boolean existsByResourceIdAndEventType(String resourceId, String eventType);

    @Modifying
    @Query(value = "INSERT INTO processed_event (event_id, source_channel, event_type, resource_id, correlation_id, consumer_group, processed_at, status, error_message, execution_duration_ms) " +
            "VALUES (:eventId, :sourceChannel, :eventType, :resourceId, :correlationId, :consumerGroup, :processedAt, :status, :errorMessage, :durationMs) " +
            "ON CONFLICT (event_id) DO NOTHING", nativeQuery = true)
    int insertIfNotExists(@Param("eventId") String eventId,
                          @Param("sourceChannel") String sourceChannel,
                          @Param("eventType") String eventType,
                          @Param("resourceId") String resourceId,
                          @Param("correlationId") String correlationId,
                          @Param("consumerGroup") String consumerGroup,
                          @Param("processedAt") Instant processedAt,
                          @Param("status") String status,
                          @Param("errorMessage") String errorMessage,
                          @Param("durationMs") Long durationMs);
}
