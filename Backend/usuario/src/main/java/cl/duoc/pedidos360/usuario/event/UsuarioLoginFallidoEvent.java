package cl.duoc.pedidos360.usuario.event;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public class UsuarioLoginFallidoEvent implements Serializable {

    private String eventId;
    private String eventType;
    private String timestamp;
    private String email;
    private String motivo;

    public UsuarioLoginFallidoEvent() {
    }

    public UsuarioLoginFallidoEvent(String email, String motivo) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = "USUARIO_LOGIN_FALLIDO";
        this.timestamp = Instant.now().toString();
        this.email = email;
        this.motivo = motivo;
    }

    public UsuarioLoginFallidoEvent(String eventId, String eventType, String timestamp, String email, String motivo) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.timestamp = timestamp;
        this.email = email;
        this.motivo = motivo;
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }
}
