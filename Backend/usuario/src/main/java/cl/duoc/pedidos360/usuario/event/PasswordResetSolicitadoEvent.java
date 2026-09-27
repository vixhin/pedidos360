package cl.duoc.pedidos360.usuario.event;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public class PasswordResetSolicitadoEvent implements Serializable {

    private String eventId;
    private String eventType;
    private String timestamp;
    private Long usuarioId;
    private String email;
    private String resetToken;

    public PasswordResetSolicitadoEvent() {
    }

    public PasswordResetSolicitadoEvent(Long usuarioId, String email, String resetToken) {
        this.eventId = UUID.randomUUID().toString();
        this.eventType = "PASSWORD_RESET_SOLICITADO";
        this.timestamp = Instant.now().toString();
        this.usuarioId = usuarioId;
        this.email = email;
        this.resetToken = resetToken;
    }

    public PasswordResetSolicitadoEvent(String eventId, String eventType, String timestamp, Long usuarioId, String email, String resetToken) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.timestamp = timestamp;
        this.usuarioId = usuarioId;
        this.email = email;
        this.resetToken = resetToken;
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

    public Long getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(Long usuarioId) {
        this.usuarioId = usuarioId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getResetToken() {
        return resetToken;
    }

    public void setResetToken(String resetToken) {
        this.resetToken = resetToken;
    }
}
