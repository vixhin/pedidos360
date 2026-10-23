package cl.duoc.pedidos360.notificacion.listener;

import cl.duoc.pedidos360.notificacion.entity.Notificacion;
import cl.duoc.pedidos360.notificacion.event.CarritoExpiradoEvent;
import cl.duoc.pedidos360.notificacion.event.PedidoCreadoEvent;
import cl.duoc.pedidos360.notificacion.event.PedidoEstadoActualizadoEvent;
import cl.duoc.pedidos360.notificacion.event.RepartidorAsignadoEvent;
import cl.duoc.pedidos360.notificacion.event.PasswordResetSolicitadoEvent;
import cl.duoc.pedidos360.notificacion.service.NotificacionService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificacionEventListenerTest {

    @Mock
    private NotificacionService notificacionService;

    @Mock
    private Channel channel;

    private NotificacionEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new NotificacionEventListener(notificacionService);
    }

    // =========================================================================
    // TESTS FUNCIONALES CON ACK EXPLICITO
    // =========================================================================

    @Test
    @DisplayName("handlePedidoCreado - crea notificacion, marca procesado y envia ACK")
    void handlePedidoCreado_debeCrearNotificacionYEnviarAck() throws IOException {
        PedidoCreadoEvent event = new PedidoCreadoEvent("evt-101", "PEDIDO_CREADO", "2026-09-27T00:00:00Z", 100L, 5L, 25000.0);

        listener.handlePedidoCreado(event, channel, 1L);

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionService, times(1)).enviarNotificacion(captor.capture());

        Notificacion notifGuardada = captor.getValue();
        assertEquals(5L, notifGuardada.getUsuarioId());
        assertTrue(notifGuardada.getMensaje().contains("#100"));
        assertTrue(notifGuardada.getMensaje().contains("25000"));
        assertTrue(listener.isEventProcessed("evt-101"));

        verify(channel).basicAck(1L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("handlePedidoCreado - ignora duplicados y envia ACK")
    void handlePedidoCreado_debeIgnorarDuplicadosConAck() throws IOException {
        PedidoCreadoEvent event = new PedidoCreadoEvent("evt-101", "PEDIDO_CREADO", "2026-09-27T00:00:00Z", 100L, 5L, 25000.0);

        listener.handlePedidoCreado(event, channel, 1L);
        listener.handlePedidoCreado(event, channel, 2L);

        verify(notificacionService, times(1)).enviarNotificacion(any(Notificacion.class));
        verify(channel).basicAck(1L, false);
        verify(channel).basicAck(2L, false);
    }

    @Test
    @DisplayName("handleCarritoExpirado - crea notificacion y envia ACK")
    void handleCarritoExpirado_debeCrearNotificacionYEnviarAck() throws IOException {
        CarritoExpiradoEvent event = new CarritoExpiradoEvent("evt-202", "CARRITO_EXPIRADO", "2026-09-27T00:00:00Z", 7L, 7L);

        listener.handleCarritoExpirado(event, channel, 1L);

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionService, times(1)).enviarNotificacion(captor.capture());

        Notificacion notifGuardada = captor.getValue();
        assertEquals(7L, notifGuardada.getUsuarioId());
        assertTrue(notifGuardada.getMensaje().contains("48 horas"));
        assertTrue(listener.isEventProcessed("evt-202"));

        verify(channel).basicAck(1L, false);
    }

    @Test
    @DisplayName("handleCarritoExpirado - ignora duplicados y envia ACK")
    void handleCarritoExpirado_debeIgnorarDuplicadosConAck() throws IOException {
        CarritoExpiradoEvent event = new CarritoExpiradoEvent("evt-202", "CARRITO_EXPIRADO", "2026-09-27T00:00:00Z", 7L, 7L);

        listener.handleCarritoExpirado(event, channel, 1L);
        listener.handleCarritoExpirado(event, channel, 2L);

        verify(notificacionService, times(1)).enviarNotificacion(any(Notificacion.class));
        verify(channel).basicAck(1L, false);
        verify(channel).basicAck(2L, false);
    }

    @Test
    @DisplayName("handlePedidoEstadoActualizado - crea notificacion y envia ACK")
    void handlePedidoEstadoActualizado_debeCrearNotificacionYEnviarAck() throws IOException {
        PedidoEstadoActualizadoEvent event =
                new PedidoEstadoActualizadoEvent("evt-505", "PEDIDO_ESTADO_ACTUALIZADO", "2026-09-27T00:00:00Z", 100L, 5L, "EN_CAMINO");

        listener.handlePedidoEstadoActualizado(event, channel, 1L);

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionService, times(1)).enviarNotificacion(captor.capture());

        Notificacion notif = captor.getValue();
        assertEquals(5L, notif.getUsuarioId());
        assertTrue(notif.getMensaje().contains("EN_CAMINO"));
        assertTrue(listener.isEventProcessed("evt-505"));
        verify(channel).basicAck(1L, false);
    }

    @Test
    @DisplayName("handleRepartidorAsignado - crea notificacion y envia ACK")
    void handleRepartidorAsignado_debeCrearNotificacionYEnviarAck() throws IOException {
        RepartidorAsignadoEvent event =
                new RepartidorAsignadoEvent("evt-606", "REPARTIDOR_ASIGNADO", "2026-09-27T00:00:00Z", 100L, 5L, 88L, "Juan Perez");

        listener.handleRepartidorAsignado(event, channel, 1L);

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionService, times(1)).enviarNotificacion(captor.capture());

        Notificacion notif = captor.getValue();
        assertEquals(5L, notif.getUsuarioId());
        assertTrue(notif.getMensaje().contains("Juan Perez"));
        assertTrue(listener.isEventProcessed("evt-606"));
        verify(channel).basicAck(1L, false);
    }

    @Test
    @DisplayName("handlePasswordResetSolicitado - crea notificacion y envia ACK")
    void handlePasswordResetSolicitado_debeCrearNotificacionYEnviarAck() throws IOException {
        PasswordResetSolicitadoEvent event =
                new PasswordResetSolicitadoEvent("evt-999", "PASSWORD_RESET_SOLICITADO", "2026-09-27T00:00:00Z", 5L, "test@pedidos360.cl", "reset-token-xyz");

        listener.handlePasswordResetSolicitado(event, channel, 1L);

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionService, times(1)).enviarNotificacion(captor.capture());

        Notificacion notif = captor.getValue();
        assertEquals(5L, notif.getUsuarioId());
        assertTrue(notif.getMensaje().contains("reset-token-xyz"));
        assertTrue(listener.isEventProcessed("evt-999"));
        verify(channel).basicAck(1L, false);
    }

    @Test
    @DisplayName("Eventos nulos envia ACK para descarte sin lanzar excepcion")
    void handleEventosNulos_enviaAckSinExcepcion() throws IOException {
        listener.handlePedidoCreado(null, channel, 1L);
        listener.handleCarritoExpirado(null, channel, 2L);
        listener.handlePedidoEstadoActualizado(null, channel, 3L);
        listener.handleRepartidorAsignado(null, channel, 4L);
        listener.handlePasswordResetSolicitado(null, channel, 5L);

        verify(notificacionService, never()).enviarNotificacion(any());
        verify(channel).basicAck(1L, false);
        verify(channel).basicAck(2L, false);
        verify(channel).basicAck(3L, false);
        verify(channel).basicAck(4L, false);
        verify(channel).basicAck(5L, false);
    }

    // =========================================================================
    // TESTS DE RESILIENCIA Y NACK
    // =========================================================================

    @Test
    @DisplayName("Resiliencia - error en servicio envia NACK sin requeue (va a DLQ)")
    void testErrorEnServicioEnviaNack() throws IOException {
        PedidoCreadoEvent event = new PedidoCreadoEvent("evt-nack-1", "PEDIDO_CREADO", "2026-09-27T00:00:00Z", 100L, 5L, 25000.0);

        doThrow(new RuntimeException("Error de persistencia"))
                .when(notificacionService).enviarNotificacion(any(Notificacion.class));

        listener.handlePedidoCreado(event, channel, 1L);

        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        verify(channel, times(1)).basicNack(1L, false, false);
        assertFalse(listener.isEventProcessed("evt-nack-1"));
    }

    @Test
    @DisplayName("Resiliencia - error en CarritoExpirado envia NACK")
    void testErrorCarritoExpiradoEnviaNack() throws IOException {
        CarritoExpiradoEvent event = new CarritoExpiradoEvent("evt-nack-2", "CARRITO_EXPIRADO", "2026-09-27T00:00:00Z", 7L, 7L);

        doThrow(new RuntimeException("Error de red"))
                .when(notificacionService).enviarNotificacion(any(Notificacion.class));

        listener.handleCarritoExpirado(event, channel, 1L);

        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        verify(channel, times(1)).basicNack(1L, false, false);
        assertFalse(listener.isEventProcessed("evt-nack-2"));
    }

    @Test
    @DisplayName("Resiliencia - despues de NACK, reintento exitoso envia ACK")
    void testReintentoExitosoEnviaAck() throws IOException {
        PedidoCreadoEvent event = new PedidoCreadoEvent("evt-retry-1", "PEDIDO_CREADO", "2026-09-27T00:00:00Z", 100L, 5L, 25000.0);

        // Primer intento falla
        doThrow(new RuntimeException("Error temporal"))
                .when(notificacionService).enviarNotificacion(any(Notificacion.class));
        listener.handlePedidoCreado(event, channel, 1L);
        verify(channel).basicNack(1L, false, false);

        // Reset del mock para que el segundo intento funcione
        reset(notificacionService);
        reset(channel);

        // Segundo intento (simulando redelivery)
        listener.handlePedidoCreado(event, channel, 2L);
        verify(channel).basicAck(2L, false);
        assertTrue(listener.isEventProcessed("evt-retry-1"));
    }
}
