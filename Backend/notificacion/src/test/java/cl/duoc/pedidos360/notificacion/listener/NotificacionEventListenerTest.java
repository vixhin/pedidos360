package cl.duoc.pedidos360.notificacion.listener;

import cl.duoc.pedidos360.notificacion.entity.Notificacion;
import cl.duoc.pedidos360.notificacion.event.CarritoExpiradoEvent;
import cl.duoc.pedidos360.notificacion.event.PedidoCreadoEvent;
import cl.duoc.pedidos360.notificacion.service.NotificacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificacionEventListenerTest {

    @Mock
    private NotificacionService notificacionService;

    private NotificacionEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new NotificacionEventListener(notificacionService);
    }

    @Test
    void handlePedidoCreado_debeCrearNotificacionYMarcarProcesado() {
        PedidoCreadoEvent event = new PedidoCreadoEvent("evt-101", "PEDIDO_CREADO", "2026-09-27T00:00:00Z", 100L, 5L, 25000.0);

        listener.handlePedidoCreado(event);

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionService, times(1)).enviarNotificacion(captor.capture());

        Notificacion notifGuardada = captor.getValue();
        assertEquals(5L, notifGuardada.getUsuarioId());
        assertTrue(notifGuardada.getMensaje().contains("#100"));
        assertTrue(notifGuardada.getMensaje().contains("25000"));
        assertTrue(listener.isEventProcessed("evt-101"));
    }

    @Test
    void handlePedidoCreado_debeIgnorarDuplicados() {
        PedidoCreadoEvent event = new PedidoCreadoEvent("evt-101", "PEDIDO_CREADO", "2026-09-27T00:00:00Z", 100L, 5L, 25000.0);

        listener.handlePedidoCreado(event);
        listener.handlePedidoCreado(event);

        verify(notificacionService, times(1)).enviarNotificacion(any(Notificacion.class));
    }

    @Test
    void handleCarritoExpirado_debeCrearNotificacionYMarcarProcesado() {
        CarritoExpiradoEvent event = new CarritoExpiradoEvent("evt-202", "CARRITO_EXPIRADO", "2026-09-27T00:00:00Z", 7L, 7L);

        listener.handleCarritoExpirado(event);

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionService, times(1)).enviarNotificacion(captor.capture());

        Notificacion notifGuardada = captor.getValue();
        assertEquals(7L, notifGuardada.getUsuarioId());
        assertTrue(notifGuardada.getMensaje().contains("48 horas"));
        assertTrue(listener.isEventProcessed("evt-202"));
    }

    @Test
    void handleCarritoExpirado_debeIgnorarDuplicados() {
        CarritoExpiradoEvent event = new CarritoExpiradoEvent("evt-202", "CARRITO_EXPIRADO", "2026-09-27T00:00:00Z", 7L, 7L);

        listener.handleCarritoExpirado(event);
        listener.handleCarritoExpirado(event);

        verify(notificacionService, times(1)).enviarNotificacion(any(Notificacion.class));
    }

    @Test
    void handlePedidoEstadoActualizado_debeCrearNotificacionYMarcarProcesado() {
        cl.duoc.pedidos360.notificacion.event.PedidoEstadoActualizadoEvent event =
                new cl.duoc.pedidos360.notificacion.event.PedidoEstadoActualizadoEvent("evt-505", "PEDIDO_ESTADO_ACTUALIZADO", "2026-09-27T00:00:00Z", 100L, 5L, "EN_CAMINO");

        listener.handlePedidoEstadoActualizado(event);

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionService, times(1)).enviarNotificacion(captor.capture());

        Notificacion notif = captor.getValue();
        assertEquals(5L, notif.getUsuarioId());
        assertTrue(notif.getMensaje().contains("EN_CAMINO"));
        assertTrue(listener.isEventProcessed("evt-505"));
    }

    @Test
    void handleRepartidorAsignado_debeCrearNotificacionYMarcarProcesado() {
        cl.duoc.pedidos360.notificacion.event.RepartidorAsignadoEvent event =
                new cl.duoc.pedidos360.notificacion.event.RepartidorAsignadoEvent("evt-606", "REPARTIDOR_ASIGNADO", "2026-09-27T00:00:00Z", 100L, 5L, 88L, "Juan Perez");

        listener.handleRepartidorAsignado(event);

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionService, times(1)).enviarNotificacion(captor.capture());

        Notificacion notif = captor.getValue();
        assertEquals(5L, notif.getUsuarioId());
        assertTrue(notif.getMensaje().contains("Juan Perez"));
        assertTrue(listener.isEventProcessed("evt-606"));
    }

    @Test
    void handlePasswordResetSolicitado_debeCrearNotificacionYMarcarProcesado() {
        cl.duoc.pedidos360.notificacion.event.PasswordResetSolicitadoEvent event =
                new cl.duoc.pedidos360.notificacion.event.PasswordResetSolicitadoEvent("evt-999", "PASSWORD_RESET_SOLICITADO", "2026-09-27T00:00:00Z", 5L, "test@pedidos360.cl", "reset-token-xyz");

        listener.handlePasswordResetSolicitado(event);

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionService, times(1)).enviarNotificacion(captor.capture());

        Notificacion notif = captor.getValue();
        assertEquals(5L, notif.getUsuarioId());
        assertTrue(notif.getMensaje().contains("reset-token-xyz"));
        assertTrue(listener.isEventProcessed("evt-999"));
    }

    @Test
    void handleEventosNulos_noDebeLanzarExcepcion() {
        listener.handlePedidoCreado(null);
        listener.handleCarritoExpirado(null);
        listener.handlePedidoEstadoActualizado(null);
        listener.handleRepartidorAsignado(null);
        listener.handlePasswordResetSolicitado(null);

        verify(notificacionService, never()).enviarNotificacion(any());
    }
}
