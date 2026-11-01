package cl.duoc.pedidos360.pedidos.dto;

import java.math.BigDecimal;
import java.util.List;

public class CotizacionRequestDTO {

    public static class ItemCotizacion {
        private Long productoId;
        private String nombre;
        private Integer cantidad;
        private BigDecimal precioUnitario;

        public ItemCotizacion() {}

        public ItemCotizacion(Long productoId, String nombre, Integer cantidad, BigDecimal precioUnitario) {
            this.productoId = productoId;
            this.nombre = nombre;
            this.cantidad = cantidad;
            this.precioUnitario = precioUnitario;
        }

        public Long getProductoId() { return productoId; }
        public void setProductoId(Long productoId) { this.productoId = productoId; }
        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }
        public Integer getCantidad() { return cantidad; }
        public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
        public BigDecimal getPrecioUnitario() { return precioUnitario; }
        public void setPrecioUnitario(BigDecimal precioUnitario) { this.precioUnitario = precioUnitario; }
    }

    private List<ItemCotizacion> items;
    private String codigoCupon;
    private String comuna;

    public CotizacionRequestDTO() {}

    public CotizacionRequestDTO(List<ItemCotizacion> items, String codigoCupon, String comuna) {
        this.items = items;
        this.codigoCupon = codigoCupon;
        this.comuna = comuna;
    }

    public List<ItemCotizacion> getItems() { return items; }
    public void setItems(List<ItemCotizacion> items) { this.items = items; }
    public String getCodigoCupon() { return codigoCupon; }
    public void setCodigoCupon(String codigoCupon) { this.codigoCupon = codigoCupon; }
    public String getComuna() { return comuna; }
    public void setComuna(String comuna) { this.comuna = comuna; }
}

