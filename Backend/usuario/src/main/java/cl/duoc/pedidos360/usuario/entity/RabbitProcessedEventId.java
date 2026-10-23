package cl.duoc.pedidos360.usuario.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class RabbitProcessedEventId implements Serializable {

    @Column(name = "event_id", nullable = false, length = 100)
    private String eventId;

    @Column(name = "consumer", nullable = false, length = 100)
    private String consumer;

    public RabbitProcessedEventId() {}

    public RabbitProcessedEventId(String eventId, String consumer) {
        this.eventId = eventId;
        this.consumer = consumer;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getConsumer() {
        return consumer;
    }

    public void setConsumer(String consumer) {
        this.consumer = consumer;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RabbitProcessedEventId that = (RabbitProcessedEventId) o;
        return Objects.equals(eventId, that.eventId) && Objects.equals(consumer, that.consumer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId, consumer);
    }
}
