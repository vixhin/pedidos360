package cl.duoc.pedidos360.productos.dto;

import cl.duoc.pedidos360.productos.entity.Producto;

public class ProductoResponseDTO {

    private Long id;
    private String sku;
    private String nombre;
    private String categoria;
    private String descripcion;
    private Double precio;
    private Integer stock;
    private String imagenUrl;

    public ProductoResponseDTO() {}

    public ProductoResponseDTO(Long id, String sku, String nombre, String categoria, String descripcion, Double precio, Integer stock, String imagenUrl) {
        this.id = id;
        this.sku = sku;
        this.nombre = nombre;
        this.categoria = categoria;
        this.descripcion = descripcion;
        this.precio = precio;
        this.stock = stock;
        this.imagenUrl = imagenUrl;
    }

    public static ProductoResponseDTO fromEntity(Producto p) {
        return new ProductoResponseDTO(
                p.getId(),
                p.getSku(),
                p.getNombre(),
                p.getCategoria(),
                p.getDescripcion(),
                p.getPrecio(),
                p.getStock(),
                p.getImagenUrl()
        );
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public Double getPrecio() {
        return precio;
    }

    public void setPrecio(Double precio) {
        this.precio = precio;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public String getImagenUrl() {
        return imagenUrl;
    }

    public void setImagenUrl(String imagenUrl) {
        this.imagenUrl = imagenUrl;
    }
}
