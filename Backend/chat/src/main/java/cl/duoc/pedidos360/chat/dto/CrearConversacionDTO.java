package cl.duoc.pedidos360.chat.dto;

public class CrearConversacionDTO {

    private Long pedidoId;
    private Long usuarioId; // Id del usuario solicitante

    public CrearConversacionDTO() {}

    public CrearConversacionDTO(Long pedidoId, Long usuarioId) {
        this.pedidoId = pedidoId;
        this.usuarioId = usuarioId;
    }

    public Long getPedidoId() { return pedidoId; }
    public void setPedidoId(Long pedidoId) { this.pedidoId = pedidoId; }

    public Long getUsuarioId() { return usuarioId; }
    public void setUsuarioId(Long usuarioId) { this.usuarioId = usuarioId; }
}
