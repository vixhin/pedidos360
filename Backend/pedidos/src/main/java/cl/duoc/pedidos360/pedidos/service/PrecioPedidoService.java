package cl.duoc.pedidos360.pedidos.service;

import cl.duoc.pedidos360.pedidos.dto.CotizacionRequestDTO;
import cl.duoc.pedidos360.pedidos.dto.CotizacionResponseDTO;
import cl.duoc.pedidos360.pedidos.entity.Pedido;
import cl.duoc.pedidos360.pedidos.entity.PedidoItem;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Recalcula en el servidor los precios y totales de un pedido antes de guardarlo.
 *
 * <p>Previene la manipulación de precios (CWE-602 / OWASP API6): el precio unitario y el nombre
 * de cada ítem se toman de productos-service, y el subtotal, envío, tarifa, descuento por cupón
 * y total se calculan con el motor comercial. Cualquier monto enviado por el cliente se descarta.</p>
 */
@Service
public class PrecioPedidoService {

    static final int CANTIDAD_MAXIMA_POR_ITEM = 99;

    private final CatalogoProductosClient catalogo;
    private final MotorComercialService motorComercial;

    public PrecioPedidoService(CatalogoProductosClient catalogo, MotorComercialService motorComercial) {
        this.catalogo = catalogo;
        this.motorComercial = motorComercial;
    }

    /**
     * Reemplaza precios y totales del pedido por los valores autoritativos.
     *
     * @throws IllegalArgumentException si el pedido es inválido (sin ítems, cantidades fuera de rango,
     *                                  producto inexistente o stock insuficiente)
     * @throws IllegalStateException    si no se puede consultar el catálogo
     */
    public CotizacionResponseDTO recalcular(Pedido pedido, String codigoCupon, String comuna) {
        List<PedidoItem> items = pedido.getItems();
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("El pedido debe tener al menos un producto.");
        }

        List<CotizacionRequestDTO.ItemCotizacion> itemsCotizacion = new ArrayList<>();
        for (PedidoItem item : items) {
            if (item.getProductoId() == null) {
                throw new IllegalArgumentException("Cada ítem debe indicar el producto.");
            }
            Integer cantidad = item.getCantidad();
            if (cantidad == null || cantidad < 1 || cantidad > CANTIDAD_MAXIMA_POR_ITEM) {
                throw new IllegalArgumentException("Cantidad inválida para el producto " + item.getProductoId()
                        + " (debe estar entre 1 y " + CANTIDAD_MAXIMA_POR_ITEM + ").");
            }

            CatalogoProductosClient.ProductoCatalogo producto = catalogo.obtener(item.getProductoId());
            if (producto.stock() < cantidad) {
                throw new IllegalArgumentException("Stock insuficiente para " + producto.nombre() + ".");
            }
            item.setPrecioUnitario(producto.precio());
            if (producto.nombre() != null) {
                item.setNombre(producto.nombre());
            }
            itemsCotizacion.add(new CotizacionRequestDTO.ItemCotizacion(
                    producto.id(), producto.nombre(), cantidad, BigDecimal.valueOf(producto.precio())));
        }

        CotizacionRequestDTO solicitud = new CotizacionRequestDTO();
        solicitud.setItems(itemsCotizacion);
        solicitud.setCodigoCupon(codigoCupon);
        solicitud.setComuna(comuna);
        CotizacionResponseDTO cotizacion = motorComercial.cotizar(solicitud);

        pedido.setSubtotal(cotizacion.getSubtotal().doubleValue());
        pedido.setCostoEnvio(cotizacion.getCostoEnvio().doubleValue());
        pedido.setTarifaServicio(cotizacion.getTarifaServicio().doubleValue());
        pedido.setTotal(cotizacion.getTotal().doubleValue());
        return cotizacion;
    }
}
