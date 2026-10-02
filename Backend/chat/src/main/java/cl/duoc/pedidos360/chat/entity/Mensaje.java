package cl.duoc.pedidos360.chat.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_mensaje")
public class Mensaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversacion_id", nullable = false)
    private Long conversacionId;

    @Column(name = "remitente_id", nullable = false)
    private Long remitenteId;

    @Column(name = "tipo_remitente", nullable = false)
    private String tipoRemitente; // CLIENTE, REPARTIDOR

    @Column(nullable = false, columnDefinition = "TEXT")
    private String contenido;

    @Column(nullable = false)
    private String estado = "ENVIADO"; // ENVIADO, ENTREGADO, LEIDO, FALLIDO

    @Column(name = "fecha_envio")
    private LocalDateTime fechaEnvio = LocalDateTime.now();

    @Column(name = "fecha_entrega")
    private LocalDateTime fechaEntrega;

    @Column(name = "fecha_lectura")
    private LocalDateTime fechaLectura;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public Mensaje() {}

    public Mensaje(Long conversacionId, Long remitenteId, String tipoRemitente, String contenido) {
        this.conversacionId = conversacionId;
        this.remitenteId = remitenteId;
        this.tipoRemitente = tipoRemitente;
        this.contenido = contenido;
        this.estado = "ENVIADO";
        this.fechaEnvio = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getConversacionId() { return conversacionId; }
    public void setConversacionId(Long conversacionId) { this.conversacionId = conversacionId; }

    public Long getRemitenteId() { return remitenteId; }
    public void setRemitenteId(Long remitenteId) { this.remitenteId = remitenteId; }

    public String getTipoRemitente() { return tipoRemitente; }
    public void setTipoRemitente(String tipoRemitente) { this.tipoRemitente = tipoRemitente; }

    public String getContenido() { return contenido; }
    public void setContenido(String contenido) { this.contenido = contenido; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDateTime getFechaEnvio() { return fechaEnvio; }
    public void setFechaEnvio(LocalDateTime fechaEnvio) { this.fechaEnvio = fechaEnvio; }

    public LocalDateTime getFechaEntrega() { return fechaEntrega; }
    public void setFechaEntrega(LocalDateTime fechaEntrega) { this.fechaEntrega = fechaEntrega; }

    public LocalDateTime getFechaLectura() { return fechaLectura; }
    public void setFechaLectura(LocalDateTime fechaLectura) { this.fechaLectura = fechaLectura; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
