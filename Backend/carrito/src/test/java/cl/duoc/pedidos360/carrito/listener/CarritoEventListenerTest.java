package cl.duoc.pedidos360.carrito.listener;

import cl.duoc.pedidos360.carrito.event.PedidoCreadoEvent;
import cl.duoc.pedidos360.carrito.service.CarritoService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CarritoEventListenerTest {

    @Mock
    private CarritoService carritoService;

    @Mock
    private Channel channel;

    @InjectMocks
    private CarritoEventListener eventListener;

    private PedidoCreadoEvent mockEvent;

    @BeforeEach
    void setUp() {
        mockEvent = new PedidoCreadoEvent("evt-12345", "pedido.creado", "2026-09-27T01:54:00Z", 100L, 10L, 25000.0);
    }

    // =========================================================================
    // TESTS FUNCIONALES Y DE ACK/NACK
    // =========================================================================

    @Test
    @DisplayName("handlePedidoCreado - vacia carrito correctamente y envia ACK explicito")
    void testHandlePedidoCreadoExitoConAck() throws IOException {
        doNothing().when(carritoService).vaciarCarrito(10L);

        eventListener.handlePedidoCreado(mockEvent, channel, 1L);

        verify(carritoService, times(1)).vaciarCarrito(10L);
        verify(channel, times(1)).basicAck(1L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        assertThat(eventListener.isEventProcessed("evt-12345")).isTrue();
    }

    @Test
    @DisplayName("handlePedidoCreado - ignora eventos duplicados y envia ACK (Idempotencia)")
    void testHandlePedidoCreadoIdempotenteConAck() throws IOException {
        doNothing().when(carritoService).vaciarCarrito(10L);

        // Primer intento
        eventListener.handlePedidoCreado(mockEvent, channel, 1L);
        // Segundo intento (duplicado)
        eventListener.handlePedidoCreado(mockEvent, channel, 2L);

        // Se debió invocar solo una vez
        verify(carritoService, times(1)).vaciarCarrito(10L);
        // Ambas entregas deben recibir ACK
        verify(channel).basicAck(1L, false);
        verify(channel).basicAck(2L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("handlePedidoCreado - evento nulo envia ACK para descarte")
    void testHandlePedidoCreadoNuloConAck() throws IOException {
        eventListener.handlePedidoCreado(null, channel, 1L);
        eventListener.handlePedidoCreado(new PedidoCreadoEvent(), channel, 2L);

        verify(channel).basicAck(1L, false);
        verify(channel).basicAck(2L, false);
        verifyNoInteractions(carritoService);
    }

    // =========================================================================
    // TESTS DE RESILIENCIA Y NACK
    // =========================================================================

    @Test
    @DisplayName("Resiliencia - error en servicio envia NACK sin requeue (va a DLQ)")
    void testErrorEnServicioEnviaNack() throws IOException {
        doThrow(new RuntimeException("Error en base de datos"))
                .when(carritoService).vaciarCarrito(10L);

        eventListener.handlePedidoCreado(mockEvent, channel, 1L);

        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        verify(channel, times(1)).basicNack(1L, false, false);
        assertThat(eventListener.isEventProcessed("evt-12345")).isFalse();
    }

    @Test
    @DisplayName("Resiliencia - evento sin usuarioId envia ACK sin procesar")
    void testEventoSinUsuarioIdEnviaAck() throws IOException {
        PedidoCreadoEvent event = new PedidoCreadoEvent("evt-999", "pedido.creado", "2026-09-27T00:00:00Z", 100L, null, 5000.0);

        eventListener.handlePedidoCreado(event, channel, 1L);

        verifyNoInteractions(carritoService);
        verify(channel, times(1)).basicAck(1L, false);
        assertThat(eventListener.isEventProcessed("evt-999")).isTrue();
    }

    @Test
    @DisplayName("Resiliencia - multiples fallos generan multiples NACKs individuales")
    void testMultiplesNacksIndividuales() throws IOException {
        PedidoCreadoEvent event1 = new PedidoCreadoEvent("evt-A", "pedido.creado", "2026-09-27T00:00:00Z", 1L, 1L, 100.0);
        PedidoCreadoEvent event2 = new PedidoCreadoEvent("evt-B", "pedido.creado", "2026-09-27T00:00:00Z", 2L, 2L, 200.0);

        doThrow(new RuntimeException("Fallo 1")).when(carritoService).vaciarCarrito(1L);
        doThrow(new RuntimeException("Fallo 2")).when(carritoService).vaciarCarrito(2L);

        eventListener.handlePedidoCreado(event1, channel, 10L);
        eventListener.handlePedidoCreado(event2, channel, 11L);

        verify(channel).basicNack(10L, false, false);
        verify(channel).basicNack(11L, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }
}
