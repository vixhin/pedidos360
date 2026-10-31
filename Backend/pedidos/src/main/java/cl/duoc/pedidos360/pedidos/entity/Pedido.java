package cl.duoc.pedidos360.pedidos.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "pedidos")
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(nullable = false)
    private Double total;

    private Double subtotal;

    @Column(name = "tarifa_servicio")
    private Double tarifaServicio = 1500.00;

    @Column(name = "costo_envio")
    private Double costoEnvio = 0.00;

    @Column(nullable = false)
    private String estado;

    @Column(name = "repartidor_id")
    private Long repartidorId;

    @Column(name = "repartidor_nombre")
    private String repartidorNombre;

    @Column(name = "repartidor_telefono")
    private String repartidorTelefono;

    @Column(name = "direccion_entrega")
    private String direccionEntrega;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonManagedReference
    private List<PedidoItem> items = new ArrayList<>();

    public Pedido() {
        this.fechaCreacion = LocalDateTime.now();
    }

    public Pedido(Long id, Long usuarioId, Double total, String estado) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.total = total;
        this.estado = estado;
        this.fechaCreacion = LocalDateTime.now();
    }

    public void addItem(PedidoItem item) {
        items.add(item);
        item.setPedido(this);
    }

    public void removeItem(PedidoItem item) {
        items.remove(item);
        item.setPedido(null);
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

    public Double getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(Double subtotal) {
        this.subtotal = subtotal;
    }

    public Double getTarifaServicio() {
        return tarifaServicio;
    }

    public void setTarifaServicio(Double tarifaServicio) {
        this.tarifaServicio = tarifaServicio;
    }

    public Double getCostoEnvio() {
        return costoEnvio;
    }

    public void setCostoEnvio(Double costoEnvio) {
        this.costoEnvio = costoEnvio;
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

    public String getRepartidorNombre() {
        return repartidorNombre;
    }

    public void setRepartidorNombre(String repartidorNombre) {
        this.repartidorNombre = repartidorNombre;
    }

    public String getRepartidorTelefono() {
        return repartidorTelefono;
    }

    public void setRepartidorTelefono(String repartidorTelefono) {
        this.repartidorTelefono = repartidorTelefono;
    }

    public String getDireccionEntrega() {
        return direccionEntrega;
    }

    public void setDireccionEntrega(String direccionEntrega) {
        this.direccionEntrega = direccionEntrega;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public List<PedidoItem> getItems() {
        return items;
    }

    public void setItems(List<PedidoItem> items) {
        this.items = items;
        if (items != null) {
            for (PedidoItem item : items) {
                item.setPedido(this);
            }
        }
    }
}
