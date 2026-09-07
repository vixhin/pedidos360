package cl.duoc.pedidos360.notificacion.service;

import cl.duoc.pedidos360.notificacion.entity.Notificacion;
import cl.duoc.pedidos360.notificacion.repository.NotificacionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificacionServiceTest {

    @Mock
    private NotificacionRepository notificacionRepository;

    @InjectMocks
    private NotificacionService notificacionService;

    private Notificacion mockNotif;

    @BeforeEach
    void setUp() {
        mockNotif = new Notificacion();
        mockNotif.setId(1L);
        mockNotif.setUsuarioId(5L);
        mockNotif.setMensaje("Tu pedido fue despachado");
    }

    @Test
    @DisplayName("obtenerTodas - retorna todas las notificaciones")
    void testObtenerTodas() {
        when(notificacionRepository.findAll()).thenReturn(List.of(mockNotif));

        List<Notificacion> resultado = notificacionService.obtenerTodas();

        assertThat(resultado).hasSize(1);
    }

    @Test
    @DisplayName("obtenerPorUsuario - retorna notificaciones del usuario")
    void testObtenerPorUsuario() {
        when(notificacionRepository.findByUsuarioId(5L)).thenReturn(List.of(mockNotif));

        List<Notificacion> resultado = notificacionService.obtenerPorUsuario(5L);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getMensaje()).isEqualTo("Tu pedido fue despachado");
    }

    @Test
    @DisplayName("enviarNotificacion - guarda y retorna la notificación")
    void testEnviarNotificacion() {
        when(notificacionRepository.save(any(Notificacion.class))).thenReturn(mockNotif);

        Notificacion guardada = notificacionService.enviarNotificacion(mockNotif);

        assertThat(guardada.getId()).isEqualTo(1L);
        verify(notificacionRepository).save(mockNotif);
    }
}
