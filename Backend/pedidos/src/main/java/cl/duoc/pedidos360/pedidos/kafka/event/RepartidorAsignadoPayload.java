package cl.duoc.pedidos360.pedidos.kafka.event;

import java.io.Serializable;

public class RepartidorAsignadoPayload implements Serializable {
    private Long usuarioId;
    private Long repartidorId;
    private String nombreRepartidor;
    private String nuevoEstado;

    public RepartidorAsignadoPayload() {
    }

    public RepartidorAsignadoPayload(Long usuarioId, Long repartidorId, String nombreRepartidor, String nuevoEstado) {
        this.usuarioId = usuarioId;
        this.repartidorId = repartidorId;
        this.nombreRepartidor = nombreRepartidor;
        this.nuevoEstado = nuevoEstado;
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

    public String getNombreRepartidor() {
        return nombreRepartidor;
    }

    public void setNombreRepartidor(String nombreRepartidor) {
        this.nombreRepartidor = nombreRepartidor;
    }

    public String getNuevoEstado() {
        return nuevoEstado;
    }

    public void setNuevoEstado(String nuevoEstado) {
        this.nuevoEstado = nuevoEstado;
    }
}

