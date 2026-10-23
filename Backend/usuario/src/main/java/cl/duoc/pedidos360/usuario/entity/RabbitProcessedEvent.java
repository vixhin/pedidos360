package cl.duoc.pedidos360.usuario.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "rabbit_processed_event")
public class RabbitProcessedEvent {

    @EmbeddedId
    private RabbitProcessedEventId id;

    @Column(name = "event_type", length = 100)
    private String eventType;

    @Column(name = "processed_at", updatable = false)
    private LocalDateTime processedAt;

    public RabbitProcessedEvent() {}

    public RabbitProcessedEvent(String eventId, String consumer, String eventType) {
        this.id = new RabbitProcessedEventId(eventId, consumer);
        this.eventType = eventType;
        this.processedAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.processedAt == null) {
            this.processedAt = LocalDateTime.now();
        }
    }

    public RabbitProcessedEventId getId() {
        return id;
    }

    public void setId(RabbitProcessedEventId id) {
        this.id = id;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }
}
