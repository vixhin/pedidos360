package cl.duoc.pedidos360.carrito.event;

import java.io.Serializable;

public class PedidoCreadoEvent implements Serializable {

    private String eventId;
    private String eventType;
    private String timestamp;
    private Long resourceId;
    private Long usuarioId;
    private Double total;

    public PedidoCreadoEvent() {
    }

    public PedidoCreadoEvent(String eventId, String eventType, String timestamp, Long resourceId, Long usuarioId, Double total) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.timestamp = timestamp;
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
