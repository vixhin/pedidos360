package cl.duoc.pedidos360.analitica.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "processed_event")
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Column(name = "source_channel", nullable = false, length = 50)
    private String sourceChannel;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "resource_id", length = 100)
    private String resourceId;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "consumer_group", length = 100)
    private String consumerGroup;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt = Instant.now();

    @Column(name = "status", length = 20)
    private String status = "COMPLETED";

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "execution_duration_ms")
    private Long executionDurationMs;

    public ProcessedEvent() {
    }

    public ProcessedEvent(String eventId, String sourceChannel, String eventType,
                          String resourceId, String correlationId, String consumerGroup,
                          Instant processedAt) {
        this(eventId, sourceChannel, eventType, resourceId, correlationId, consumerGroup, processedAt, "COMPLETED", null, null);
    }

    public ProcessedEvent(String eventId, String sourceChannel, String eventType,
                          String resourceId, String correlationId, String consumerGroup,
                          Instant processedAt, String status, String errorMessage, Long executionDurationMs) {
        this.eventId = eventId;
        this.sourceChannel = sourceChannel;
        this.eventType = eventType;
        this.resourceId = resourceId;
        this.correlationId = correlationId;
        this.consumerGroup = consumerGroup;
        this.processedAt = processedAt != null ? processedAt : Instant.now();
        this.status = status != null ? status : "COMPLETED";
        this.errorMessage = errorMessage;
        this.executionDurationMs = executionDurationMs;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getSourceChannel() {
        return sourceChannel;
    }

    public void setSourceChannel(String sourceChannel) {
        this.sourceChannel = sourceChannel;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Long getExecutionDurationMs() {
        return executionDurationMs;
    }

    public void setExecutionDurationMs(Long executionDurationMs) {
        this.executionDurationMs = executionDurationMs;
    }
}
