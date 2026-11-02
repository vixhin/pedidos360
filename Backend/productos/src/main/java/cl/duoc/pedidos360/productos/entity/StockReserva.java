package cl.duoc.pedidos360.productos.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_reservas")
public class StockReserva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reserva_id", nullable = false, unique = true, length = 128)
    private String reservaId;

    @Column(name = "pedido_id")
    private Long pedidoId;

    @Column(name = "estado", nullable = false, length = 50)
    private String estado; // 'RESERVADO', 'CONFIRMADO', 'LIBERADO'

    @Column(name = "items_json", nullable = false, columnDefinition = "TEXT")
    private String itemsJson;

    @Column(name = "creado_el", nullable = false)
    private LocalDateTime creadoEl;

    @Column(name = "expira_el", nullable = false)
    private LocalDateTime expiraEl;

    public StockReserva() {}

    public StockReserva(String reservaId, Long pedidoId, String estado, String itemsJson, LocalDateTime expiraEl) {
        this.reservaId = reservaId;
        this.pedidoId = pedidoId;
        this.estado = estado;
        this.itemsJson = itemsJson;
        this.creadoEl = LocalDateTime.now();
        this.expiraEl = expiraEl;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getReservaId() { return reservaId; }
    public void setReservaId(String reservaId) { this.reservaId = reservaId; }

    public Long getPedidoId() { return pedidoId; }
    public void setPedidoId(Long pedidoId) { this.pedidoId = pedidoId; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getItemsJson() { return itemsJson; }
    public void setItemsJson(String itemsJson) { this.itemsJson = itemsJson; }

    public LocalDateTime getCreadoEl() { return creadoEl; }
    public void setCreadoEl(LocalDateTime creadoEl) { this.creadoEl = creadoEl; }

    public LocalDateTime getExpiraEl() { return expiraEl; }
    public void setExpiraEl(LocalDateTime expiraEl) { this.expiraEl = expiraEl; }
}

