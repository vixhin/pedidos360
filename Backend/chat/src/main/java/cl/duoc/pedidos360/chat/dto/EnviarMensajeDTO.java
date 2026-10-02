package cl.duoc.pedidos360.chat.dto;

public class EnviarMensajeDTO {

    private Long conversacionId;
    private Long remitenteId;
    private String tipoRemitente; // CLIENTE, REPARTIDOR
    private String contenido;

    public EnviarMensajeDTO() {}

    public EnviarMensajeDTO(Long conversacionId, Long remitenteId, String tipoRemitente, String contenido) {
        this.conversacionId = conversacionId;
        this.remitenteId = remitenteId;
        this.tipoRemitente = tipoRemitente;
        this.contenido = contenido;
    }

    public Long getConversacionId() { return conversacionId; }
    public void setConversacionId(Long conversacionId) { this.conversacionId = conversacionId; }

    public Long getRemitenteId() { return remitenteId; }
    public void setRemitenteId(Long remitenteId) { this.remitenteId = remitenteId; }

    public String getTipoRemitente() { return tipoRemitente; }
    public void setTipoRemitente(String tipoRemitente) { this.tipoRemitente = tipoRemitente; }

    public String getContenido() { return contenido; }
    public void setContenido(String contenido) { this.contenido = contenido; }
}
