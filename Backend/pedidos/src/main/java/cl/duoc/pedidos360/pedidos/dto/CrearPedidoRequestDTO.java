package cl.duoc.pedidos360.pedidos.dto;

import cl.duoc.pedidos360.pedidos.entity.Pedido;
import cl.duoc.pedidos360.pedidos.entity.PedidoItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Datos que el cliente puede enviar al crear un pedido.
 * Excluye id, estado, repartidor, fecha e idempotencyKey (esta llega por cabecera):
 * el servidor los asigna para evitar asignación masiva.
 * Los montos (subtotal, costoEnvio, tarifaServicio, total, precioUnitario) se aceptan por
 * compatibilidad, pero el servidor los recalcula siempre (ver PrecioPedidoService).
 */
public record CrearPedidoRequestDTO(
        Long usuarioId,
        Double subtotal,
        Double costoEnvio,
        Double tarifaServicio,
        Double total,
        String direccionEntrega,
        List<ItemDTO> items,
        String codigoCupon,
        String comuna) {

    public record ItemDTO(Long productoId, String nombre, String icono, Integer cantidad, Double precioUnitario) {

        PedidoItem toEntity() {
            PedidoItem item = new PedidoItem();
            item.setProductoId(productoId);
            item.setNombre(nombre);
            item.setIcono(icono);
            item.setCantidad(cantidad);
            item.setPrecioUnitario(precioUnitario);
            return item;
        }
    }

    public Pedido toEntity() {
        Pedido pedido = new Pedido();
        pedido.setUsuarioId(usuarioId);
        pedido.setSubtotal(subtotal);
        if (costoEnvio != null) {
            pedido.setCostoEnvio(costoEnvio);
        }
        if (tarifaServicio != null) {
            pedido.setTarifaServicio(tarifaServicio);
        }
        pedido.setTotal(total);
        pedido.setDireccionEntrega(direccionEntrega);
        List<PedidoItem> entidades = new ArrayList<>();
        if (items != null) {
            for (ItemDTO item : items) {
                entidades.add(item.toEntity());
            }
        }
        pedido.setItems(entidades);
        return pedido;
    }
}
