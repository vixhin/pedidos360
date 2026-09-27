package cl.duoc.pedidos360.carrito.event;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public class CarritoExpiradoEvent implements Serializable {

    private String eventId;
    private String eventType;
    private String timestamp;
    private Long resourceId;
    private Long usuarioId;

    public CarritoExpiradoEvent() {
    }

    public CarritoExpiradoEvent(Long usuarioId) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = "carrito.expirado";
        this.timestamp = Instant.now().toString();
        this.resourceId = usuarioId;
        this.usuarioId = usuarioId;
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

    public Long getResourceId() {
        return resourceId;
    }

    public void setResourceId(Long resourceId) {
        this.resourceId = resourceId;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(Long usuarioId) {
        this.usuarioId = usuarioId;
    }
}
