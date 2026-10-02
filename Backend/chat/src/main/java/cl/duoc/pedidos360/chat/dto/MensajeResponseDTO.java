package cl.duoc.pedidos360.chat.dto;

import java.time.LocalDateTime;

public class MensajeResponseDTO {

    private Long id;
    private Long conversacionId;
    private Long remitenteId;
    private String tipoRemitente;
    private String contenido;
    private String estado;
    private LocalDateTime fechaEnvio;
    private LocalDateTime fechaEntrega;
    private LocalDateTime fechaLectura;

    public MensajeResponseDTO() {}

    public MensajeResponseDTO(Long id, Long conversacionId, Long remitenteId, String tipoRemitente,
                              String contenido, String estado, LocalDateTime fechaEnvio,
                              LocalDateTime fechaEntrega, LocalDateTime fechaLectura) {
        this.id = id;
        this.conversacionId = conversacionId;
        this.remitenteId = remitenteId;
        this.tipoRemitente = tipoRemitente;
        this.contenido = contenido;
        this.estado = estado;
        this.fechaEnvio = fechaEnvio;
        this.fechaEntrega = fechaEntrega;
        this.fechaLectura = fechaLectura;
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
}
