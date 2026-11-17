package cl.duoc.pedidos360.pedidos.service;

import cl.duoc.pedidos360.pedidos.dto.CotizacionRequestDTO;
import cl.duoc.pedidos360.pedidos.dto.CotizacionResponseDTO;
import cl.duoc.pedidos360.pedidos.entity.Pedido;
import cl.duoc.pedidos360.pedidos.entity.PedidoItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Pruebas de seguridad: el servidor ignora los precios y totales enviados por el cliente. */
@ExtendWith(MockitoExtension.class)
class PrecioPedidoServiceTest {

    @Mock
    private CatalogoProductosClient catalogo;

    @Mock
    private MotorComercialService motorComercial;

    @InjectMocks
    private PrecioPedidoService precioPedidoService;

    private Pedido pedidoManipulado(int cantidad) {
        PedidoItem item = new PedidoItem();
        item.setProductoId(1L);
        item.setNombre("Nombre falso");
        item.setCantidad(cantidad);
        item.setPrecioUnitario(1.0); // precio manipulado por el cliente
        Pedido pedido = new Pedido();
        pedido.setUsuarioId(10L);
        pedido.setTotal(1.0); // total manipulado por el cliente
        pedido.setItems(new ArrayList<>(List.of(item)));
        return pedido;
    }

    private CotizacionResponseDTO cotizacion(String subtotal, String envio, String tarifa, String total) {
        CotizacionResponseDTO c = new CotizacionResponseDTO();
        c.setSubtotal(new BigDecimal(subtotal));
        c.setCostoEnvio(new BigDecimal(envio));
        c.setTarifaServicio(new BigDecimal(tarifa));
        c.setTotal(new BigDecimal(total));
        return c;
    }

    @Test
    @DisplayName("precio y total manipulados se reemplazan por los del catálogo y el motor comercial")
    void reemplazaPreciosManipulados() {
        Pedido pedido = pedidoManipulado(2);
        when(catalogo.obtener(1L)).thenReturn(new CatalogoProductosClient.ProductoCatalogo(1L, "Leche Entera", 1190.0, 50));
        when(motorComercial.cotizar(any())).thenReturn(cotizacion("2380", "2990", "1500", "6870"));

        precioPedidoService.recalcular(pedido, "DESCUENTO10", "Santiago");

        assertThat(pedido.getItems().get(0).getPrecioUnitario()).isEqualTo(1190.0);
        assertThat(pedido.getItems().get(0).getNombre()).isEqualTo("Leche Entera");
        assertThat(pedido.getSubtotal()).isEqualTo(2380.0);
        assertThat(pedido.getTotal()).isEqualTo(6870.0);

        ArgumentCaptor<CotizacionRequestDTO> captor = ArgumentCaptor.forClass(CotizacionRequestDTO.class);
        verify(motorComercial).cotizar(captor.capture());
        assertThat(captor.getValue().getItems().get(0).getPrecioUnitario()).isEqualByComparingTo("1190");
        assertThat(captor.getValue().getCodigoCupon()).isEqualTo("DESCUENTO10");
        assertThat(captor.getValue().getComuna()).isEqualTo("Santiago");
    }

    @Test
    @DisplayName("cantidad 0 o negativa se rechaza sin consultar el catálogo")
    void rechazaCantidadInvalida() {
        assertThatThrownBy(() -> precioPedidoService.recalcular(pedidoManipulado(0), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> precioPedidoService.recalcular(pedidoManipulado(-3), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(catalogo, motorComercial);
    }

    @Test
    @DisplayName("cantidad mayor al stock disponible se rechaza")
    void rechazaStockInsuficiente() {
        when(catalogo.obtener(1L)).thenReturn(new CatalogoProductosClient.ProductoCatalogo(1L, "Leche Entera", 1190.0, 1));

        assertThatThrownBy(() -> precioPedidoService.recalcular(pedidoManipulado(5), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Stock insuficiente");
        verifyNoInteractions(motorComercial);
    }

    @Test
    @DisplayName("pedido sin ítems se rechaza")
    void rechazaPedidoVacio() {
        Pedido pedido = new Pedido();
        pedido.setItems(new ArrayList<>());

        assertThatThrownBy(() -> precioPedidoService.recalcular(pedido, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("si el catálogo no responde, el pedido no se crea (falla cerrado)")
    void fallaCerradoSiCatalogoNoDisponible() {
        when(catalogo.obtener(1L)).thenThrow(new IllegalStateException("Catálogo de productos no disponible"));

        assertThatThrownBy(() -> precioPedidoService.recalcular(pedidoManipulado(1), null, null))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(motorComercial);
    }
}
