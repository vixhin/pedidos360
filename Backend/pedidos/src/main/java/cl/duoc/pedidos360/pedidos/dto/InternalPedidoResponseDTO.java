package cl.duoc.pedidos360.pedidos.dto;

public class InternalPedidoResponseDTO {

    private Long id;
    private Long usuarioId;
    private Long repartidorId;
    private String estado;
    private Double total;

    public InternalPedidoResponseDTO() {
    }

    public InternalPedidoResponseDTO(Long id, Long usuarioId, Long repartidorId, String estado, Double total) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.repartidorId = repartidorId;
        this.estado = estado;
        this.total = total;
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

    public Long getRepartidorId() {
        return repartidorId;
    }

    public void setRepartidorId(Long repartidorId) {
        this.repartidorId = repartidorId;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public Double getTotal() {
        return total;
    }

    public void setTotal(Double total) {
        this.total = total;
    }
}
