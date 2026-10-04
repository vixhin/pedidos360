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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalPedidoControllerTest {

    @Mock
    private PedidoService pedidoService;

    private InternalPedidoController internalPedidoController;
    private final String validInternalKey = "pedidos360-internal-secret-key-2026";
    private Pedido mockPedido;

    @BeforeEach
    void setUp() {
        internalPedidoController = new InternalPedidoController(pedidoService, validInternalKey);
        mockPedido = new Pedido();
        mockPedido.setId(100L);
        mockPedido.setUsuarioId(5L);
        mockPedido.setRepartidorId(8L);
        mockPedido.setEstado("EN_CAMINO");
        mockPedido.setTotal(15000.0);
    }

    @Test
    @DisplayName("1. GET /api/internal/pedidos/{id} con key válida -> 200 OK y DTO reducido")
    void testObtenerPorIdInternalConKeyValidaReturns200() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Internal-Service-Key", validInternalKey);

        when(pedidoService.obtenerPorId(100L)).thenReturn(Optional.of(mockPedido));

        ResponseEntity<?> response = internalPedidoController.obtenerPorIdInternal(request, 100L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("2. GET /api/internal/pedidos/{id} sin key -> 403 Forbidden")
    void testObtenerPorIdInternalSinKeyReturns403() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseEntity<?> response = internalPedidoController.obtenerPorIdInternal(request, 100L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("3. GET /api/internal/pedidos/{id} con key incorrecta -> 403 Forbidden")
    void testObtenerPorIdInternalConKeyInvalidaReturns403() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Internal-Service-Key", "clave-falsa");

        ResponseEntity<?> response = internalPedidoController.obtenerPorIdInternal(request, 100L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("4. GET /api/internal/pedidos/{id} pedido inexistente con key válida -> 404 Not Found")
    void testObtenerPorIdInternalNoExisteReturns404() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Internal-Service-Key", validInternalKey);

        when(pedidoService.obtenerPorId(999L)).thenReturn(Optional.empty());

        ResponseEntity<?> response = internalPedidoController.obtenerPorIdInternal(request, 999L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
