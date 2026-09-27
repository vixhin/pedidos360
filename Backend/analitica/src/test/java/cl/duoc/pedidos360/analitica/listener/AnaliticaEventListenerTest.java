package cl.duoc.pedidos360.analitica.listener;

import cl.duoc.pedidos360.analitica.entity.AnaliticaEvento;
import cl.duoc.pedidos360.analitica.event.CarritoExpiradoEvent;
import cl.duoc.pedidos360.analitica.event.PedidoCreadoEvent;
import cl.duoc.pedidos360.analitica.service.AnaliticaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnaliticaEventListenerTest {

    @Mock
    private AnaliticaService analiticaService;

    private AnaliticaEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new AnaliticaEventListener(analiticaService);
    }

    @Test
    void handlePedidoCreado_debeRegistrarEventoYMarcarProcesado() {
        PedidoCreadoEvent event = new PedidoCreadoEvent("evt-301", "PEDIDO_CREADO", "2026-09-27T00:00:00Z", 200L, 10L, 45000.0);

        listener.handlePedidoCreado(event);

        ArgumentCaptor<AnaliticaEvento> captor = ArgumentCaptor.forClass(AnaliticaEvento.class);
        verify(analiticaService, times(1)).registrarEvento(captor.capture());

        AnaliticaEvento registrado = captor.getValue();
        assertEquals("PEDIDO_CREADO", registrado.getTipoEvento());
        assertTrue(registrado.getDescripcion().contains("#200"));
        assertTrue(registrado.getDescripcion().contains("45000"));
        assertTrue(listener.isEventProcessed("evt-301"));
    }

    @Test
    void handlePedidoCreado_debeIgnorarDuplicados() {
        PedidoCreadoEvent event = new PedidoCreadoEvent("evt-301", "PEDIDO_CREADO", "2026-09-27T00:00:00Z", 200L, 10L, 45000.0);

        listener.handlePedidoCreado(event);
        listener.handlePedidoCreado(event);

        verify(analiticaService, times(1)).registrarEvento(any(AnaliticaEvento.class));
    }

    @Test
    void handleCarritoExpirado_debeRegistrarEventoYMarcarProcesado() {
        CarritoExpiradoEvent event = new CarritoExpiradoEvent("evt-402", "CARRITO_EXPIRADO", "2026-09-27T00:00:00Z", 15L, 15L);

        listener.handleCarritoExpirado(event);

        ArgumentCaptor<AnaliticaEvento> captor = ArgumentCaptor.forClass(AnaliticaEvento.class);
        verify(analiticaService, times(1)).registrarEvento(captor.capture());

        AnaliticaEvento registrado = captor.getValue();
        assertEquals("CARRITO_EXPIRADO", registrado.getTipoEvento());
        assertTrue(registrado.getDescripcion().contains("usuarioId=15"));
        assertTrue(listener.isEventProcessed("evt-402"));
    }

    @Test
    void handleCarritoExpirado_debeIgnorarDuplicados() {
        CarritoExpiradoEvent event = new CarritoExpiradoEvent("evt-402", "CARRITO_EXPIRADO", "2026-09-27T00:00:00Z", 15L, 15L);

        listener.handleCarritoExpirado(event);
        listener.handleCarritoExpirado(event);

        verify(analiticaService, times(1)).registrarEvento(any(AnaliticaEvento.class));
    }

    @Test
    void handlePedidoEstadoActualizado_debeRegistrarEventoYMarcarProcesado() {
        cl.duoc.pedidos360.analitica.event.PedidoEstadoActualizadoEvent event =
                new cl.duoc.pedidos360.analitica.event.PedidoEstadoActualizadoEvent("evt-707", "PEDIDO_ESTADO_ACTUALIZADO", "2026-09-27T00:00:00Z", 200L, 10L, "ENTREGADO");

        listener.handlePedidoEstadoActualizado(event);

        ArgumentCaptor<AnaliticaEvento> captor = ArgumentCaptor.forClass(AnaliticaEvento.class);
        verify(analiticaService, times(1)).registrarEvento(captor.capture());

        AnaliticaEvento registrado = captor.getValue();
        assertEquals("PEDIDO_ESTADO_ACTUALIZADO", registrado.getTipoEvento());
        assertTrue(registrado.getDescripcion().contains("ENTREGADO"));
        assertTrue(listener.isEventProcessed("evt-707"));
    }

    @Test
    void handleRepartidorAsignado_debeRegistrarEventoYMarcarProcesado() {
        cl.duoc.pedidos360.analitica.event.RepartidorAsignadoEvent event =
                new cl.duoc.pedidos360.analitica.event.RepartidorAsignadoEvent("evt-808", "REPARTIDOR_ASIGNADO", "2026-09-27T00:00:00Z", 200L, 10L, 55L, "Pedro Gomez");

        listener.handleRepartidorAsignado(event);

        ArgumentCaptor<AnaliticaEvento> captor = ArgumentCaptor.forClass(AnaliticaEvento.class);
        verify(analiticaService, times(1)).registrarEvento(captor.capture());

        AnaliticaEvento registrado = captor.getValue();
        assertEquals("REPARTIDOR_ASIGNADO", registrado.getTipoEvento());
        assertTrue(registrado.getDescripcion().contains("Pedro Gomez"));
        assertTrue(listener.isEventProcessed("evt-808"));
    }

    @Test
    void handleUsuarioLoginExitoso_debeRegistrarEventoYMarcarProcesado() {
        cl.duoc.pedidos360.analitica.event.UsuarioLoginExitosoEvent event =
                new cl.duoc.pedidos360.analitica.event.UsuarioLoginExitosoEvent("evt-909", "USUARIO_LOGIN_EXITOSO", "2026-09-27T00:00:00Z", 10L, "test@pedidos360.cl", "CLIENTE");

        listener.handleUsuarioLoginExitoso(event);

        ArgumentCaptor<AnaliticaEvento> captor = ArgumentCaptor.forClass(AnaliticaEvento.class);
        verify(analiticaService, times(1)).registrarEvento(captor.capture());

        AnaliticaEvento registrado = captor.getValue();
        assertEquals("USUARIO_LOGIN_EXITOSO", registrado.getTipoEvento());
        assertTrue(registrado.getDescripcion().contains("test@pedidos360.cl"));
        assertTrue(listener.isEventProcessed("evt-909"));
    }

    @Test
    void handleUsuarioLoginFallido_debeRegistrarEventoYMarcarProcesado() {
        cl.duoc.pedidos360.analitica.event.UsuarioLoginFallidoEvent event =
                new cl.duoc.pedidos360.analitica.event.UsuarioLoginFallidoEvent("evt-910", "USUARIO_LOGIN_FALLIDO", "2026-09-27T00:00:00Z", "bad@pedidos360.cl", "Contraseña incorrecta");

        listener.handleUsuarioLoginFallido(event);

        ArgumentCaptor<AnaliticaEvento> captor = ArgumentCaptor.forClass(AnaliticaEvento.class);
        verify(analiticaService, times(1)).registrarEvento(captor.capture());

        AnaliticaEvento registrado = captor.getValue();
        assertEquals("USUARIO_LOGIN_FALLIDO", registrado.getTipoEvento());
        assertTrue(registrado.getDescripcion().contains("bad@pedidos360.cl"));
        assertTrue(listener.isEventProcessed("evt-910"));
    }

    @Test
    void handleEventosNulos_noDebeLanzarExcepcion() {
        listener.handlePedidoCreado(null);
        listener.handleCarritoExpirado(null);
        listener.handlePedidoEstadoActualizado(null);
        listener.handleRepartidorAsignado(null);
        listener.handleUsuarioLoginExitoso(null);
        listener.handleUsuarioLoginFallido(null);

        verify(analiticaService, never()).registrarEvento(any());
    }
}
