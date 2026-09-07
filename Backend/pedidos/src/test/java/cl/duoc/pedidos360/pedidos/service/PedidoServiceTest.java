package cl.duoc.pedidos360.pedidos.service;

import cl.duoc.pedidos360.pedidos.entity.Pedido;
import cl.duoc.pedidos360.pedidos.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;

    @InjectMocks
    private PedidoService pedidoService;

    private Pedido mockPedido;

    @BeforeEach
    void setUp() {
        mockPedido = new Pedido();
        mockPedido.setId(1L);
        mockPedido.setUsuarioId(10L);
        mockPedido.setTotal(15000.0);
        mockPedido.setEstado("PENDIENTE");
    }

    @Test
    @DisplayName("obtenerTodos - retorna todos los pedidos")
    void testObtenerTodos() {
        when(pedidoRepository.findAll()).thenReturn(List.of(mockPedido));

        List<Pedido> resultado = pedidoService.obtenerTodos();

        assertThat(resultado).hasSize(1);
    }

    @Test
    @DisplayName("obtenerPorId - retorna pedido si existe")
    void testObtenerPorIdExito() {
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(mockPedido));

        Optional<Pedido> res = pedidoService.obtenerPorId(1L);

        assertThat(res).isPresent();
        assertThat(res.get().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("obtenerPorUsuario - retorna pedidos por ID de usuario")
    void testObtenerPorUsuario() {
        when(pedidoRepository.findByUsuarioId(10L)).thenReturn(List.of(mockPedido));

        List<Pedido> resultado = pedidoService.obtenerPorUsuario(10L);

        assertThat(resultado).hasSize(1);
    }

    @Test
    @DisplayName("guardar - guarda pedido correctamente")
    void testGuardar() {
        when(pedidoRepository.save(any(Pedido.class))).thenReturn(mockPedido);

        Pedido guardado = pedidoService.guardar(mockPedido);

        assertThat(guardado.getId()).isEqualTo(1L);
        verify(pedidoRepository).save(mockPedido);
    }

    @Test
    @DisplayName("eliminar - elimina pedido por ID")
    void testEliminar() {
        doNothing().when(pedidoRepository).deleteById(1L);

        pedidoService.eliminar(1L);

        verify(pedidoRepository).deleteById(1L);
    }
}
