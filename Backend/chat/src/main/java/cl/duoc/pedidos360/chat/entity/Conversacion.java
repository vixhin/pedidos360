package cl.duoc.pedidos360.chat.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_conversacion")
public class Conversacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pedido_id", nullable = false)
    private Long pedidoId;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    @Column(name = "repartidor_id", nullable = false)
    private Long repartidorId;

    @Column(nullable = false)
    private String estado = "ABIERTO"; // ABIERTO, CERRADO, ELIMINADO

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;

    @Column(name = "deleted_for_client_at")
    private LocalDateTime deletedForClientAt;

    @Column(name = "deleted_for_courier_at")
    private LocalDateTime deletedForCourierAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Conversacion() {}

    public Conversacion(Long pedidoId, Long clienteId, Long repartidorId) {
        this.pedidoId = pedidoId;
        this.clienteId = clienteId;
        this.repartidorId = repartidorId;
        this.estado = "ABIERTO";
        this.fechaCreacion = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
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

    public LocalDateTime getDeletedForClientAt() { return deletedForClientAt; }
    public void setDeletedForClientAt(LocalDateTime deletedForClientAt) { this.deletedForClientAt = deletedForClientAt; }

    public LocalDateTime getDeletedForCourierAt() { return deletedForCourierAt; }
    public void setDeletedForCourierAt(LocalDateTime deletedForCourierAt) { this.deletedForCourierAt = deletedForCourierAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
