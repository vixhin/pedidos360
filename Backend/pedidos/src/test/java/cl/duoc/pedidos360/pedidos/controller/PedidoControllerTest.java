package cl.duoc.pedidos360.pedidos.controller;

import cl.duoc.pedidos360.pedidos.entity.Pedido;
import cl.duoc.pedidos360.pedidos.service.PedidoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidoControllerTest {

    @Mock
    private PedidoService pedidoService;

    @InjectMocks
    private PedidoController pedidoController;

    private Pedido mockPedido;
    private final String validKey = "valid-key-2026";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(pedidoController, "bffInternalKey", validKey);
        mockPedido = new Pedido();
        mockPedido.setId(1L);
        mockPedido.setUsuarioId(10L);
        mockPedido.setTotal(25000.0);
    }

    private MockHttpServletRequest createAuthRequest(String emailOrId, String role) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Internal-Service-Key", validKey);
        request.addHeader("X-User-Email", emailOrId);
        if (role != null) {
            request.addHeader("X-User-Roles", role);
        }
        return request;
    }

    @Test
    @DisplayName("1. cliente crea pedido con usuarioId falso -> backend fuerza callerUserId")
    void testClienteCreaPedidoConUsuarioIdFalso() {
        MockHttpServletRequest request = createAuthRequest("10", "CLIENTE");
        Pedido pedidoEntrante = new Pedido();
        pedidoEntrante.setUsuarioId(999L); // Falso

        when(pedidoService.guardar(any(Pedido.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<?> response = pedidoController.crear(pedidoEntrante, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Pedido creado = (Pedido) response.getBody();
        assertThat(creado.getUsuarioId()).isEqualTo(10L); // Forzado a 10L
    }

    @Test
    @DisplayName("2. cliente cambia estado -> 403 Forbidden")
    void testClienteCambiaEstadoReturns403() {
        MockHttpServletRequest request = createAuthRequest("10", "CLIENTE");
        when(pedidoService.obtenerPorId(1L)).thenReturn(Optional.of(mockPedido));

        ResponseEntity<?> response = pedidoController.actualizarEstado(1L, "ENTREGADO", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("3. cliente asigna repartidor -> 403 Forbidden")
    void testClienteAsignaRepartidorReturns403() {
        MockHttpServletRequest request = createAuthRequest("10", "CLIENTE");
        when(pedidoService.obtenerPorId(1L)).thenReturn(Optional.of(mockPedido));

        ResponseEntity<?> response = pedidoController.asignarRepartidor(1L, 99L, "Carlos", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("4. cliente elimina pedido -> 403 Forbidden")
    void testClienteEliminaPedidoReturns403() {
        MockHttpServletRequest request = createAuthRequest("10", "CLIENTE");

        ResponseEntity<?> response = pedidoController.eliminar(1L, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("5. cliente consulta pedidos de otro usuario -> 403 Forbidden")
    void testClienteConsultaPedidosDeOtroReturns403() {
        MockHttpServletRequest request = createAuthRequest("10", "CLIENTE");

        ResponseEntity<?> response = pedidoController.obtenerPorUsuario(999L, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("6. cliente consulta sus propios pedidos -> 200 OK")
    void testClienteConsultaSusPropiosPedidosExito() {
        MockHttpServletRequest request = createAuthRequest("10", "CLIENTE");
        when(pedidoService.obtenerPorUsuario(10L)).thenReturn(List.of(mockPedido));

        ResponseEntity<?> response = pedidoController.obtenerPorUsuario(10L, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("7. repartidor acepta pedido libre -> éxito 200")
    void testRepartidorAceptaPedidoLibreExito() {
        MockHttpServletRequest request = createAuthRequest("15", "REPARTIDOR");
        mockPedido.setRepartidorId(null);
        when(pedidoService.obtenerPorId(1L)).thenReturn(Optional.of(mockPedido));
        when(pedidoService.asignarRepartidor(1L, 15L, "Repartidor")).thenReturn(mockPedido);

        ResponseEntity<?> response = pedidoController.asignarRepartidor(1L, 999L, "Repartidor", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("8. repartidor intenta asignarlo a otro ID -> se ignora ID externo y usa callerUserId")
    void testRepartidorIgnoraIdExternoEnAsignacion() {
        MockHttpServletRequest request = createAuthRequest("15", "REPARTIDOR");
        mockPedido.setRepartidorId(null);
        when(pedidoService.obtenerPorId(1L)).thenReturn(Optional.of(mockPedido));
        when(pedidoService.asignarRepartidor(1L, 15L, "Repartidor")).thenReturn(mockPedido);

        ResponseEntity<?> response = pedidoController.asignarRepartidor(1L, 888L, "Repartidor", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("9. repartidor cambia estado de pedido propio -> 200 OK")
    void testRepartidorCambiaEstadoPedidoPropioExito() {
        MockHttpServletRequest request = createAuthRequest("15", "REPARTIDOR");
        mockPedido.setRepartidorId(15L);
        when(pedidoService.obtenerPorId(1L)).thenReturn(Optional.of(mockPedido));
        when(pedidoService.actualizarEstado(1L, "EN_CAMINO", 15L, "REPARTIDOR")).thenReturn(mockPedido);

        ResponseEntity<?> response = pedidoController.actualizarEstado(1L, "EN_CAMINO", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("10. repartidor cambia pedido ajeno -> 403 Forbidden")
    void testRepartidorCambiaPedidoAjenoReturns403() {
        MockHttpServletRequest request = createAuthRequest("15", "REPARTIDOR");
        mockPedido.setRepartidorId(99L); // Asignado a otro
        when(pedidoService.obtenerPorId(1L)).thenReturn(Optional.of(mockPedido));

        ResponseEntity<?> response = pedidoController.actualizarEstado(1L, "ENTREGADO", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("11. repartidor intenta aceptar pedido de otro -> 409 Conflict")
    void testRepartidorAceptaPedidoDeOtroReturns409() {
        MockHttpServletRequest request = createAuthRequest("15", "REPARTIDOR");
        mockPedido.setRepartidorId(99L); // Ya asignado a 99L
        when(pedidoService.obtenerPorId(1L)).thenReturn(Optional.of(mockPedido));

        ResponseEntity<?> response = pedidoController.asignarRepartidor(1L, 15L, "Repartidor", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("12. vendedor cambia estado -> permitido 200 OK")
    void testVendedorCambiaEstadoPermitido() {
        MockHttpServletRequest request = createAuthRequest("20", "VENDEDOR");
        when(pedidoService.obtenerPorId(1L)).thenReturn(Optional.of(mockPedido));
        when(pedidoService.actualizarEstado(1L, "PREPARANDO", 20L, "VENDEDOR")).thenReturn(mockPedido);

        ResponseEntity<?> response = pedidoController.actualizarEstado(1L, "PREPARANDO", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("13. vendedor asigna repartidor -> permitido 200 OK")
    void testVendedorAsignaRepartidorPermitido() {
        MockHttpServletRequest request = createAuthRequest("20", "VENDEDOR");
        when(pedidoService.obtenerPorId(1L)).thenReturn(Optional.of(mockPedido));
        when(pedidoService.asignarRepartidor(1L, 99L, "Carlos")).thenReturn(mockPedido);

        ResponseEntity<?> response = pedidoController.asignarRepartidor(1L, 99L, "Carlos", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("14. admin puede eliminar -> 204 No Content")
    void testAdminPuedeEliminar() {
        MockHttpServletRequest request = createAuthRequest("1", "ADMIN");
        doNothing().when(pedidoService).eliminar(1L);

        ResponseEntity<?> response = pedidoController.eliminar(1L, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("15. admin puede cambiar estado -> 200 OK")
    void testAdminPuedeCambiarEstado() {
        MockHttpServletRequest request = createAuthRequest("1", "ADMIN");
        when(pedidoService.obtenerPorId(1L)).thenReturn(Optional.of(mockPedido));
        when(pedidoService.actualizarEstado(1L, "CANCELADO", 1L, "ADMIN")).thenReturn(mockPedido);

        ResponseEntity<?> response = pedidoController.actualizarEstado(1L, "CANCELADO", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("16. request sin JWT y sin service key -> 403 Forbidden")
    void testRequestSinAuthReturns403() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseEntity<?> response = pedidoController.actualizarEstado(1L, "ENTREGADO", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("17. X-User-Id falsificado sin service key -> 403 Forbidden")
    void testXUserIdFalsificadoSinServiceKeyReturns403() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "999"); // Spoofed header without S2S Key

        ResponseEntity<?> response = pedidoController.actualizarEstado(1L, "ENTREGADO", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("18. service key válida pero usuario no resoluble -> 403 Forbidden")
    void testServiceKeyValidaSinUsuarioResolubleReturns403() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Internal-Service-Key", validKey);
        // No email or X-User-Id sent

        ResponseEntity<?> response = pedidoController.actualizarEstado(1L, "ENTREGADO", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
