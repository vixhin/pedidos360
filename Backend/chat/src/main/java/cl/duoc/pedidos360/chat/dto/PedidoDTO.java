package cl.duoc.pedidos360.chat.dto;

public class PedidoDTO {
    private Long id;
    private Long usuarioId;
    private Double total;
    private String estado;
    private Long repartidorId;

    public PedidoDTO() {}

    public PedidoDTO(Long id, Long usuarioId, Double total, String estado, Long repartidorId) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.total = total;
        this.estado = estado;
        this.repartidorId = repartidorId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public Long getRepartidorId() {
        return repartidorId;
    }

    public void setRepartidorId(Long repartidorId) {
        this.repartidorId = repartidorId;
    }
}
