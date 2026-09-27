package cl.duoc.pedidos360.pedidos.event;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public class RepartidorAsignadoEvent implements Serializable {

    private String eventId;
    private String eventType;
    private String timestamp;
    private Long resourceId;
    private Long usuarioId;
    private Long repartidorId;
    private String nombreRepartidor;

    public RepartidorAsignadoEvent() {
    }

    public RepartidorAsignadoEvent(Long pedidoId, Long usuarioId, Long repartidorId, String nombreRepartidor) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = "REPARTIDOR_ASIGNADO";
        this.timestamp = Instant.now().toString();
        this.resourceId = pedidoId;
        this.usuarioId = usuarioId;
        this.repartidorId = repartidorId;
        this.nombreRepartidor = nombreRepartidor;
    }

    public RepartidorAsignadoEvent(String eventId, String eventType, String timestamp, Long resourceId, Long usuarioId, Long repartidorId, String nombreRepartidor) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.timestamp = timestamp;
        this.resourceId = resourceId;
        this.usuarioId = usuarioId;
        this.repartidorId = repartidorId;
        this.nombreRepartidor = nombreRepartidor;
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

    public Long getRepartidorId() {
        return repartidorId;
    }

    public void setRepartidorId(Long repartidorId) {
        this.repartidorId = repartidorId;
    }

    public String getNombreRepartidor() {
        return nombreRepartidor;
    }

    public void setNombreRepartidor(String nombreRepartidor) {
        this.nombreRepartidor = nombreRepartidor;
    }
}
