package cl.duoc.pedidos360.analitica.listener;

import cl.duoc.pedidos360.analitica.entity.AnaliticaEvento;
import cl.duoc.pedidos360.analitica.event.CarritoExpiradoEvent;
import cl.duoc.pedidos360.analitica.event.PedidoCreadoEvent;
import cl.duoc.pedidos360.analitica.event.PedidoEstadoActualizadoEvent;
import cl.duoc.pedidos360.analitica.event.RepartidorAsignadoEvent;
import cl.duoc.pedidos360.analitica.event.UsuarioLoginExitosoEvent;
import cl.duoc.pedidos360.analitica.event.UsuarioLoginFallidoEvent;
import cl.duoc.pedidos360.analitica.service.AnaliticaService;
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
class AnaliticaEventListenerTest {

    @Mock
    private AnaliticaService analiticaService;

    @Mock
    private Channel channel;

    private AnaliticaEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new AnaliticaEventListener(analiticaService);
    }

    // =========================================================================
    // TESTS FUNCIONALES CON ACK EXPLICITO
    // =========================================================================

    @Test
    @DisplayName("handlePedidoCreado - registra evento, marca procesado y envia ACK")
    void handlePedidoCreado_debeRegistrarEventoYEnviarAck() throws IOException {
        PedidoCreadoEvent event = new PedidoCreadoEvent("evt-301", "PEDIDO_CREADO", "2026-09-27T00:00:00Z", 200L, 10L, 45000.0);

        listener.handlePedidoCreado(event, channel, 1L);

        ArgumentCaptor<AnaliticaEvento> captor = ArgumentCaptor.forClass(AnaliticaEvento.class);
        verify(analiticaService, times(1)).registrarEvento(captor.capture());

        AnaliticaEvento registrado = captor.getValue();
        assertEquals("PEDIDO_CREADO", registrado.getTipoEvento());
        assertTrue(registrado.getDescripcion().contains("#200"));
        assertTrue(registrado.getDescripcion().contains("45000"));
        assertTrue(listener.isEventProcessed("evt-301"));

        verify(channel).basicAck(1L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("handlePedidoCreado - ignora duplicados y envia ACK")
    void handlePedidoCreado_debeIgnorarDuplicadosConAck() throws IOException {
        PedidoCreadoEvent event = new PedidoCreadoEvent("evt-301", "PEDIDO_CREADO", "2026-09-27T00:00:00Z", 200L, 10L, 45000.0);

        listener.handlePedidoCreado(event, channel, 1L);
        listener.handlePedidoCreado(event, channel, 2L);

        verify(analiticaService, times(1)).registrarEvento(any(AnaliticaEvento.class));
        verify(channel).basicAck(1L, false);
        verify(channel).basicAck(2L, false);
    }

    @Test
    @DisplayName("handleCarritoExpirado - registra evento y envia ACK")
    void handleCarritoExpirado_debeRegistrarEventoYEnviarAck() throws IOException {
        CarritoExpiradoEvent event = new CarritoExpiradoEvent("evt-402", "CARRITO_EXPIRADO", "2026-09-27T00:00:00Z", 15L, 15L);

        listener.handleCarritoExpirado(event, channel, 1L);

        ArgumentCaptor<AnaliticaEvento> captor = ArgumentCaptor.forClass(AnaliticaEvento.class);
        verify(analiticaService, times(1)).registrarEvento(captor.capture());

        AnaliticaEvento registrado = captor.getValue();
        assertEquals("CARRITO_EXPIRADO", registrado.getTipoEvento());
        assertTrue(registrado.getDescripcion().contains("usuarioId=15"));
        assertTrue(listener.isEventProcessed("evt-402"));
        verify(channel).basicAck(1L, false);
    }

    @Test
    @DisplayName("handleCarritoExpirado - ignora duplicados y envia ACK")
    void handleCarritoExpirado_debeIgnorarDuplicadosConAck() throws IOException {
        CarritoExpiradoEvent event = new CarritoExpiradoEvent("evt-402", "CARRITO_EXPIRADO", "2026-09-27T00:00:00Z", 15L, 15L);

        listener.handleCarritoExpirado(event, channel, 1L);
        listener.handleCarritoExpirado(event, channel, 2L);

        verify(analiticaService, times(1)).registrarEvento(any(AnaliticaEvento.class));
        verify(channel).basicAck(1L, false);
        verify(channel).basicAck(2L, false);
    }

    @Test
    @DisplayName("handlePedidoEstadoActualizado - registra evento y envia ACK")
    void handlePedidoEstadoActualizado_debeRegistrarEventoYEnviarAck() throws IOException {
        PedidoEstadoActualizadoEvent event =
                new PedidoEstadoActualizadoEvent("evt-707", "PEDIDO_ESTADO_ACTUALIZADO", "2026-09-27T00:00:00Z", 200L, 10L, "ENTREGADO");

        listener.handlePedidoEstadoActualizado(event, channel, 1L);

        ArgumentCaptor<AnaliticaEvento> captor = ArgumentCaptor.forClass(AnaliticaEvento.class);
        verify(analiticaService, times(1)).registrarEvento(captor.capture());

        AnaliticaEvento registrado = captor.getValue();
        assertEquals("PEDIDO_ESTADO_ACTUALIZADO", registrado.getTipoEvento());
        assertTrue(registrado.getDescripcion().contains("ENTREGADO"));
        assertTrue(listener.isEventProcessed("evt-707"));
        verify(channel).basicAck(1L, false);
    }

    @Test
    @DisplayName("handleRepartidorAsignado - registra evento y envia ACK")
    void handleRepartidorAsignado_debeRegistrarEventoYEnviarAck() throws IOException {
        RepartidorAsignadoEvent event =
                new RepartidorAsignadoEvent("evt-808", "REPARTIDOR_ASIGNADO", "2026-09-27T00:00:00Z", 200L, 10L, 55L, "Pedro Gomez");

        listener.handleRepartidorAsignado(event, channel, 1L);

        ArgumentCaptor<AnaliticaEvento> captor = ArgumentCaptor.forClass(AnaliticaEvento.class);
        verify(analiticaService, times(1)).registrarEvento(captor.capture());

        AnaliticaEvento registrado = captor.getValue();
        assertEquals("REPARTIDOR_ASIGNADO", registrado.getTipoEvento());
        assertTrue(registrado.getDescripcion().contains("Pedro Gomez"));
        assertTrue(listener.isEventProcessed("evt-808"));
        verify(channel).basicAck(1L, false);
    }

    @Test
    @DisplayName("handleUsuarioLoginExitoso - registra evento y envia ACK")
    void handleUsuarioLoginExitoso_debeRegistrarEventoYEnviarAck() throws IOException {
        UsuarioLoginExitosoEvent event =
                new UsuarioLoginExitosoEvent("evt-909", "USUARIO_LOGIN_EXITOSO", "2026-09-27T00:00:00Z", 10L, "test@pedidos360.cl", "CLIENTE");

        listener.handleUsuarioLoginExitoso(event, channel, 1L);

        ArgumentCaptor<AnaliticaEvento> captor = ArgumentCaptor.forClass(AnaliticaEvento.class);
        verify(analiticaService, times(1)).registrarEvento(captor.capture());

        AnaliticaEvento registrado = captor.getValue();
        assertEquals("USUARIO_LOGIN_EXITOSO", registrado.getTipoEvento());
        assertTrue(registrado.getDescripcion().contains("test@pedidos360.cl"));
        assertTrue(listener.isEventProcessed("evt-909"));
        verify(channel).basicAck(1L, false);
    }

    @Test
    @DisplayName("handleUsuarioLoginFallido - registra evento y envia ACK")
    void handleUsuarioLoginFallido_debeRegistrarEventoYEnviarAck() throws IOException {
        UsuarioLoginFallidoEvent event =
                new UsuarioLoginFallidoEvent("evt-910", "USUARIO_LOGIN_FALLIDO", "2026-09-27T00:00:00Z", "bad@pedidos360.cl", "Contraseña incorrecta");

        listener.handleUsuarioLoginFallido(event, channel, 1L);

        ArgumentCaptor<AnaliticaEvento> captor = ArgumentCaptor.forClass(AnaliticaEvento.class);
        verify(analiticaService, times(1)).registrarEvento(captor.capture());

        AnaliticaEvento registrado = captor.getValue();
        assertEquals("USUARIO_LOGIN_FALLIDO", registrado.getTipoEvento());
        assertTrue(registrado.getDescripcion().contains("bad@pedidos360.cl"));
        assertTrue(listener.isEventProcessed("evt-910"));
        verify(channel).basicAck(1L, false);
    }

    @Test
    @DisplayName("Eventos nulos envia ACK para descarte sin lanzar excepcion")
    void handleEventosNulos_enviaAckSinExcepcion() throws IOException {
        listener.handlePedidoCreado(null, channel, 1L);
        listener.handleCarritoExpirado(null, channel, 2L);
        listener.handlePedidoEstadoActualizado(null, channel, 3L);
        listener.handleRepartidorAsignado(null, channel, 4L);
        listener.handleUsuarioLoginExitoso(null, channel, 5L);
        listener.handleUsuarioLoginFallido(null, channel, 6L);

        verify(analiticaService, never()).registrarEvento(any());
        verify(channel).basicAck(1L, false);
        verify(channel).basicAck(2L, false);
        verify(channel).basicAck(3L, false);
        verify(channel).basicAck(4L, false);
        verify(channel).basicAck(5L, false);
        verify(channel).basicAck(6L, false);
    }

    // =========================================================================
    // TESTS DE RESILIENCIA Y NACK
    // =========================================================================

    @Test
    @DisplayName("Resiliencia - error en servicio envia NACK sin requeue (va a DLQ)")
    void testErrorEnServicioEnviaNack() throws IOException {
        PedidoCreadoEvent event = new PedidoCreadoEvent("evt-nack-1", "PEDIDO_CREADO", "2026-09-27T00:00:00Z", 200L, 10L, 45000.0);

        doThrow(new RuntimeException("Error de persistencia"))
                .when(analiticaService).registrarEvento(any(AnaliticaEvento.class));

        listener.handlePedidoCreado(event, channel, 1L);

        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        verify(channel, times(1)).basicNack(1L, false, false);
        assertFalse(listener.isEventProcessed("evt-nack-1"));
    }

    @Test
    @DisplayName("Resiliencia - error en CarritoExpirado envia NACK")
    void testErrorCarritoExpiradoEnviaNack() throws IOException {
        CarritoExpiradoEvent event = new CarritoExpiradoEvent("evt-nack-2", "CARRITO_EXPIRADO", "2026-09-27T00:00:00Z", 15L, 15L);

        doThrow(new RuntimeException("Error de conexion"))
                .when(analiticaService).registrarEvento(any(AnaliticaEvento.class));

        listener.handleCarritoExpirado(event, channel, 1L);

        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        verify(channel, times(1)).basicNack(1L, false, false);
        assertFalse(listener.isEventProcessed("evt-nack-2"));
    }

    @Test
    @DisplayName("Resiliencia - despues de NACK, reintento exitoso envia ACK")
    void testReintentoExitosoEnviaAck() throws IOException {
        PedidoCreadoEvent event = new PedidoCreadoEvent("evt-retry-1", "PEDIDO_CREADO", "2026-09-27T00:00:00Z", 200L, 10L, 45000.0);

        // Primer intento falla
        doThrow(new RuntimeException("Error temporal"))
                .when(analiticaService).registrarEvento(any(AnaliticaEvento.class));
        listener.handlePedidoCreado(event, channel, 1L);
        verify(channel).basicNack(1L, false, false);

        // Reset del mock para segundo intento exitoso
        reset(analiticaService);
        reset(channel);

        listener.handlePedidoCreado(event, channel, 2L);
        verify(channel).basicAck(2L, false);
        assertTrue(listener.isEventProcessed("evt-retry-1"));
    }

    @Test
    @DisplayName("Resiliencia - multiples fallos generan NACKs individuales por deliveryTag")
    void testMultiplesNacksIndividuales() throws IOException {
        PedidoCreadoEvent event1 = new PedidoCreadoEvent("evt-A", "PEDIDO_CREADO", "2026-09-27T00:00:00Z", 1L, 1L, 100.0);
        CarritoExpiradoEvent event2 = new CarritoExpiradoEvent("evt-B", "CARRITO_EXPIRADO", "2026-09-27T00:00:00Z", 2L, 2L);

        doThrow(new RuntimeException("Error")).when(analiticaService).registrarEvento(any(AnaliticaEvento.class));

        listener.handlePedidoCreado(event1, channel, 10L);
        listener.handleCarritoExpirado(event2, channel, 11L);

        verify(channel).basicNack(10L, false, false);
        verify(channel).basicNack(11L, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }
}
