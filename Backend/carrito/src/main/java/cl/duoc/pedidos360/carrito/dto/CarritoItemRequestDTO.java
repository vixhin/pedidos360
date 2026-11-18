package cl.duoc.pedidos360.carrito.dto;

import cl.duoc.pedidos360.carrito.entity.CarritoItem;

/**
 * Datos que el cliente puede enviar al agregar un ítem al carrito.
 * No expone id ni ultimaActividad: los asigna el servidor (evita asignación masiva).
 */
public record CarritoItemRequestDTO(Long usuarioId, Long productoId, Integer cantidad, Double precioUnitario) {

    public CarritoItem toEntity() {
        CarritoItem item = new CarritoItem();
        item.setUsuarioId(usuarioId);
        item.setProductoId(productoId);
        item.setCantidad(cantidad);
        item.setPrecioUnitario(precioUnitario);
        return item;
    }
}
