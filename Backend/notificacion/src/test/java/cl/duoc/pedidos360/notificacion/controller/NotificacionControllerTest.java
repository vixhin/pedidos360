package cl.duoc.pedidos360.notificacion.controller;

import cl.duoc.pedidos360.notificacion.entity.Notificacion;
import cl.duoc.pedidos360.notificacion.service.NotificacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificacionControllerTest {

    @Mock
    private NotificacionService notificacionService;

    @InjectMocks
    private NotificacionController notificacionController;

    private Notificacion mockNotif;

    @BeforeEach
    void setUp() {
        mockNotif = new Notificacion();
        mockNotif.setId(1L);
        mockNotif.setUsuarioId(5L);
        mockNotif.setMensaje("Test message");
    }

    @Test
    @DisplayName("listar - retorna HTTP 200 con lista de notificaciones")
    void testListar() {
        when(notificacionService.obtenerTodas()).thenReturn(List.of(mockNotif));

        ResponseEntity<List<Notificacion>> response = notificacionController.listar();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    @DisplayName("obtenerPorUsuario - retorna HTTP 200 con notificaciones")
    void testObtenerPorUsuario() {
        when(notificacionService.obtenerPorUsuario(5L)).thenReturn(List.of(mockNotif));

        ResponseEntity<List<Notificacion>> response = notificacionController.obtenerPorUsuario(5L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("enviar - retorna HTTP 200 con notificacion enviada")
    void testEnviar() {
        when(notificacionService.enviarNotificacion(any(Notificacion.class))).thenReturn(mockNotif);

        ResponseEntity<Notificacion> response = notificacionController.enviar(mockNotif);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getId()).isEqualTo(1L);
    }
}
