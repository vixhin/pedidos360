package cl.duoc.pedidos360.notificacion.controller;

import cl.duoc.pedidos360.notificacion.dto.NotificacionRequestDTO;
import cl.duoc.pedidos360.notificacion.entity.Notificacion;
import cl.duoc.pedidos360.notificacion.service.NotificacionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pruebas de seguridad (IDOR / suplantación) del servicio de notificaciones. */
@ExtendWith(MockitoExtension.class)
class NotificacionIdorTest {

    @Mock
    private NotificacionService notificacionService;

    @InjectMocks
    private NotificacionController notificacionController;

    private MockHttpServletRequest solicitud(Long userId, String role) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("p360.userId", userId);
        request.setAttribute("p360.role", role);
        return request;
    }

    @Test
    @DisplayName("cliente no puede marcar como leída la notificación de otro usuario -> 403")
    void clienteNoMarcaNotificacionAjena() {
        Notificacion ajena = new Notificacion();
        ajena.setId(3L);
        ajena.setUsuarioId(20L);
        when(notificacionService.obtenerPorId(3L)).thenReturn(Optional.of(ajena));

        assertThatThrownBy(() -> notificacionController.marcarLeida(3L, solicitud(10L, "CLIENTE")))
                .isInstanceOf(ResponseStatusException.class);
        verify(notificacionService, never()).marcarLeida(anyLong());
    }

    @Test
    @DisplayName("cliente no puede crear notificaciones a nombre de otro usuario: se fuerza su propio id")
    void clienteNoSuplantaDestinatario() {
        when(notificacionService.enviarNotificacion(any(Notificacion.class))).thenAnswer(inv -> inv.getArgument(0));

        notificacionController.enviar(new NotificacionRequestDTO(20L, "Mensaje falso", "PEDIDOS", null), solicitud(10L, "CLIENTE"));

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionService).enviarNotificacion(captor.capture());
        assertThat(captor.getValue().getUsuarioId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("vendedor sí puede notificar a un cliente")
    void vendedorNotificaACliente() {
        when(notificacionService.enviarNotificacion(any(Notificacion.class))).thenAnswer(inv -> inv.getArgument(0));

        notificacionController.enviar(new NotificacionRequestDTO(20L, "Tu pedido salió", "PEDIDOS", null), solicitud(2L, "VENDEDOR"));

        ArgumentCaptor<Notificacion> captor = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionService).enviarNotificacion(captor.capture());
        assertThat(captor.getValue().getUsuarioId()).isEqualTo(20L);
    }
}
