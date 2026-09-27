package cl.duoc.pedidos360.carrito.listener;

import cl.duoc.pedidos360.carrito.event.PedidoCreadoEvent;
import cl.duoc.pedidos360.carrito.service.CarritoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CarritoEventListenerTest {

    @Mock
    private CarritoService carritoService;

    @InjectMocks
    private CarritoEventListener eventListener;

    private PedidoCreadoEvent mockEvent;

    @BeforeEach
    void setUp() {
        mockEvent = new PedidoCreadoEvent("evt-12345", "pedido.creado", "2026-09-27T01:54:00Z", 100L, 10L, 25000.0);
    }

    @Test
    @DisplayName("handlePedidoCreado - vacia carrito correctamente en primer intento")
    void testHandlePedidoCreadoExito() {
        doNothing().when(carritoService).vaciarCarrito(10L);

        eventListener.handlePedidoCreado(mockEvent);

        verify(carritoService, times(1)).vaciarCarrito(10L);
        assertThat(eventListener.isEventProcessed("evt-12345")).isTrue();
    }

    @Test
    @DisplayName("handlePedidoCreado - ignora eventos duplicados (Idempotencia)")
    void testHandlePedidoCreadoIdempotente() {
        doNothing().when(carritoService).vaciarCarrito(10L);

        // Primer intento
        eventListener.handlePedidoCreado(mockEvent);
        // Segundo intento (duplicado)
        eventListener.handlePedidoCreado(mockEvent);

        // Se debió invocar solo una vez
        verify(carritoService, times(1)).vaciarCarrito(10L);
    }

    @Test
    @DisplayName("handlePedidoCreado - ignora evento nulo o sin eventId")
    void testHandlePedidoCreadoNulo() {
        eventListener.handlePedidoCreado(null);
        eventListener.handlePedidoCreado(new PedidoCreadoEvent());

        verifyNoInteractions(carritoService);
    }
}
