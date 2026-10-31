package cl.duoc.pedidos360.pedidos.kafka.event;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Envoltorio canónico para eventos distribuidos mediante Apache Kafka en Pedidos360.
 * Contiene metadatos de trazabilidad, correlación e identificación de recurso.
 */
public class KafkaEvent<T> implements Serializable {

    private String eventId;
    private String eventType;
    private String timestamp;
    private String correlationId;
    private Long resourceId;
    private T payload;

    public KafkaEvent() {
    }

    public KafkaEvent(String eventId, String eventType, String timestamp, String correlationId, Long resourceId, T payload) {
        this.eventId = (eventId != null) ? eventId : UUID.randomUUID().toString();
        this.eventType = eventType;
        this.timestamp = (timestamp != null) ? timestamp : Instant.now().toString();
        this.correlationId = (correlationId != null) ? correlationId : UUID.randomUUID().toString();
        this.resourceId = resourceId;
        this.payload = payload;
    }

    public static <T> KafkaEvent<T> of(String eventType, Long resourceId, String correlationId, T payload) {
        return new KafkaEvent<>(
                UUID.randomUUID().toString(),
                eventType,
                Instant.now().toString(),
                correlationId != null ? correlationId : UUID.randomUUID().toString(),
                resourceId,
                payload
        );
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }

    public T getPayload() {
        return payload;
    }

    public void setPayload(T payload) {
        this.payload = payload;
    }

    @Override
    public String toString() {
        return "KafkaEvent{" +
                "eventId='" + eventId + '\'' +
                ", eventType='" + eventType + '\'' +
                ", timestamp='" + timestamp + '\'' +
                ", correlationId='" + correlationId + '\'' +
                ", resourceId=" + resourceId +
                ", payload=" + payload +
                '}';
    }
}

