package cl.duoc.pedidos360.pedidos.service;

import cl.duoc.pedidos360.pedidos.config.RabbitMQConfig;
import cl.duoc.pedidos360.pedidos.entity.Pedido;
import cl.duoc.pedidos360.pedidos.event.PedidoCreadoEvent;
import cl.duoc.pedidos360.pedidos.repository.PedidoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

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
    @DisplayName("guardar - guarda pedido y publica evento pedido.creado")
    void testGuardarPublicaEvento() {
        when(pedidoRepository.save(any(Pedido.class))).thenReturn(mockPedido);

        Pedido guardado = pedidoService.guardar(mockPedido);

        assertThat(guardado.getId()).isEqualTo(1L);
        verify(pedidoRepository).save(mockPedido);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_EVENTS),
                eq(RabbitMQConfig.ROUTING_KEY_PEDIDO_CREADO),
                any(PedidoCreadoEvent.class)
        );
    }

    @Test
    @DisplayName("eliminar - elimina pedido por ID")
    void testEliminar() {
        doNothing().when(pedidoRepository).deleteById(1L);

        pedidoService.eliminar(1L);

        verify(pedidoRepository).deleteById(1L);
    }

    @Test
    @DisplayName("actualizarEstado - actualiza estado y publica evento pedido.estado.actualizado")
    void testActualizarEstadoPublicaEvento() {
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(mockPedido));
        when(pedidoRepository.save(any(Pedido.class))).thenReturn(mockPedido);

        Pedido actualizado = pedidoService.actualizarEstado(1L, "ENTREGADO");

        assertThat(actualizado.getEstado()).isEqualTo("ENTREGADO");
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_EVENTS),
                eq(RabbitMQConfig.ROUTING_KEY_PEDIDO_ESTADO_ACTUALIZADO),
                any(cl.duoc.pedidos360.pedidos.event.PedidoEstadoActualizadoEvent.class)
        );
    }

    @Test
    @DisplayName("asignarRepartidor - asigna repartidor y publica evento pedido.repartidor.asignado")
    void testAsignarRepartidorPublicaEvento() {
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(mockPedido));
        when(pedidoRepository.save(any(Pedido.class))).thenReturn(mockPedido);

        Pedido actualizado = pedidoService.asignarRepartidor(1L, 99L, "Carlos Repartidor");

        assertThat(actualizado.getRepartidorId()).isEqualTo(99L);
        assertThat(actualizado.getEstado()).isEqualTo("EN_CAMINO");
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_EVENTS),
                eq(RabbitMQConfig.ROUTING_KEY_REPARTIDOR_ASIGNADO),
                any(cl.duoc.pedidos360.pedidos.event.RepartidorAsignadoEvent.class)
        );
    }

    @Test
    @DisplayName("actualizarEstado - repartidor intentando modificar pedido ajeno lanza SecurityException")
    void testActualizarEstado_RepartidorAjeno_LanzaSecurityException() {
        mockPedido.setRepartidorId(15L);
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(mockPedido));

        org.junit.jupiter.api.Assertions.assertThrows(
                SecurityException.class,
                () -> pedidoService.actualizarEstado(1L, "ENTREGADO", 88L, "REPARTIDOR")
        );
    }

    @Test
    @DisplayName("asignarRepartidor - intentar asignar pedido ya asignado a otro lanza IllegalStateException")
    void testAsignarRepartidor_YaAsignado_LanzaIllegalStateException() {
        mockPedido.setRepartidorId(15L);
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(mockPedido));

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> pedidoService.asignarRepartidor(1L, 99L, "Otro Repartidor")
        );
    }
}
