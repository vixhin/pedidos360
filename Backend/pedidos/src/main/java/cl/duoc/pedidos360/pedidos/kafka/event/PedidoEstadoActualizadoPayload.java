package cl.duoc.pedidos360.pedidos.kafka.event;

import java.io.Serializable;

public class PedidoEstadoActualizadoPayload implements Serializable {
    private Long usuarioId;
    private String nuevoEstado;
    private String anteriorEstado;

    public PedidoEstadoActualizadoPayload() {
    }

    public PedidoEstadoActualizadoPayload(Long usuarioId, String nuevoEstado, String anteriorEstado) {
        this.usuarioId = usuarioId;
        this.nuevoEstado = nuevoEstado;
        this.anteriorEstado = anteriorEstado;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(Long usuarioId) {
        this.usuarioId = usuarioId;
    }

    public String getNuevoEstado() {
        return nuevoEstado;
    }

    public void setNuevoEstado(String nuevoEstado) {
        this.nuevoEstado = nuevoEstado;
    }

    public String getAnteriorEstado() {
        return anteriorEstado;
    }

    public void setAnteriorEstado(String anteriorEstado) {
        this.anteriorEstado = anteriorEstado;
    }
}

