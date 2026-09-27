package cl.duoc.pedidos360.pedidos.event;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public class PedidoCreadoEvent implements Serializable {

    private String eventId;
    private String eventType;
    private String timestamp;
    private Long resourceId;
    private Long usuarioId;
    private Double total;

    public PedidoCreadoEvent() {
    }

    public PedidoCreadoEvent(Long resourceId, Long usuarioId, Double total) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = "pedido.creado";
        this.timestamp = Instant.now().toString();
        this.resourceId = resourceId;
        this.usuarioId = usuarioId;
        this.total = total;
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

    public Double getTotal() {
        return total;
    }

    public void setTotal(Double total) {
        this.total = total;
    }
}
