package cl.duoc.pedidos360.pedidos.kafka.event;

import java.io.Serializable;

public class PedidoCreadoPayload implements Serializable {
    private Long usuarioId;
    private Double total;
    private String estado;

    public PedidoCreadoPayload() {
    }

    public PedidoCreadoPayload(Long usuarioId, Double total, String estado) {
        this.usuarioId = usuarioId;
        this.total = total;
        this.estado = estado;
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
}

