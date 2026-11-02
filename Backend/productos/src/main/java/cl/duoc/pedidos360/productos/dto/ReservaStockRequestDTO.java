package cl.duoc.pedidos360.productos.dto;

import java.util.List;

public class ReservaStockRequestDTO {
    private String reservaId;
    private Long pedidoId;
    private List<ItemReservaDTO> items;

    public ReservaStockRequestDTO() {}

    public ReservaStockRequestDTO(String reservaId, Long pedidoId, List<ItemReservaDTO> items) {
        this.reservaId = reservaId;
        this.pedidoId = pedidoId;
        this.items = items;
    }

    public String getReservaId() { return reservaId; }
    public void setReservaId(String reservaId) { this.reservaId = reservaId; }

    public Long getPedidoId() { return pedidoId; }
    public void setPedidoId(Long pedidoId) { this.pedidoId = pedidoId; }

    public List<ItemReservaDTO> getItems() { return items; }
    public void setItems(List<ItemReservaDTO> items) { this.items = items; }
}

