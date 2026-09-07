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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedidoControllerTest {

    @Mock
    private PedidoService pedidoService;

    @InjectMocks
    private PedidoController pedidoController;

    private Pedido mockPedido;

    @BeforeEach
    void setUp() {
        mockPedido = new Pedido();
        mockPedido.setId(1L);
        mockPedido.setUsuarioId(10L);
        mockPedido.setTotal(25000.0);
    }

    @Test
    @DisplayName("listar - retorna HTTP 200 con la lista de pedidos")
    void testListar() {
        when(pedidoService.obtenerTodos()).thenReturn(List.of(mockPedido));

        ResponseEntity<List<Pedido>> response = pedidoController.listar();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    @DisplayName("obtenerPorId - retorna HTTP 200 si existe")
    void testObtenerPorIdExito() {
        when(pedidoService.obtenerPorId(1L)).thenReturn(Optional.of(mockPedido));

        ResponseEntity<Pedido> response = pedidoController.obtenerPorId(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("obtenerPorId - retorna HTTP 404 si no existe")
    void testObtenerPorIdNoEncontrado() {
        when(pedidoService.obtenerPorId(99L)).thenReturn(Optional.empty());

        ResponseEntity<Pedido> response = pedidoController.obtenerPorId(99L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("obtenerPorUsuario - retorna HTTP 200")
    void testObtenerPorUsuario() {
        when(pedidoService.obtenerPorUsuario(10L)).thenReturn(List.of(mockPedido));

        ResponseEntity<List<Pedido>> response = pedidoController.obtenerPorUsuario(10L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("crear - retorna HTTP 200 con pedido guardado")
    void testCrear() {
        when(pedidoService.guardar(any(Pedido.class))).thenReturn(mockPedido);

        ResponseEntity<Pedido> response = pedidoController.crear(mockPedido);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("eliminar - retorna HTTP 204 NO CONTENT")
    void testEliminar() {
        doNothing().when(pedidoService).eliminar(1L);

        ResponseEntity<Void> response = pedidoController.eliminar(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
