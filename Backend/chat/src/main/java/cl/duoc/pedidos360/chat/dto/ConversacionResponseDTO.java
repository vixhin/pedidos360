package cl.duoc.pedidos360.chat.dto;

import java.time.LocalDateTime;
import java.util.List;

public class ConversacionResponseDTO {

    private Long id;
    private Long pedidoId;
    private Long clienteId;
    private Long repartidorId;
    private String estado;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaCierre;
    private List<MensajeResponseDTO> mensajes;

    public ConversacionResponseDTO() {}

    public ConversacionResponseDTO(Long id, Long pedidoId, Long clienteId, Long repartidorId,
                                   String estado, LocalDateTime fechaCreacion, LocalDateTime fechaCierre,
                                   List<MensajeResponseDTO> mensajes) {
        this.id = id;
        this.pedidoId = pedidoId;
        this.clienteId = clienteId;
        this.repartidorId = repartidorId;
        this.estado = estado;
        this.fechaCreacion = fechaCreacion;
        this.fechaCierre = fechaCierre;
        this.mensajes = mensajes;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getPedidoId() { return pedidoId; }
    public void setPedidoId(Long pedidoId) { this.pedidoId = pedidoId; }

    public Long getClienteId() { return clienteId; }
    public void setClienteId(Long clienteId) { this.clienteId = clienteId; }

    public Long getRepartidorId() { return repartidorId; }
    public void setRepartidorId(Long repartidorId) { this.repartidorId = repartidorId; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public LocalDateTime getFechaCierre() { return fechaCierre; }
    public void setFechaCierre(LocalDateTime fechaCierre) { this.fechaCierre = fechaCierre; }

    public List<MensajeResponseDTO> getMensajes() { return mensajes; }
    public void setMensajes(List<MensajeResponseDTO> mensajes) { this.mensajes = mensajes; }
}
