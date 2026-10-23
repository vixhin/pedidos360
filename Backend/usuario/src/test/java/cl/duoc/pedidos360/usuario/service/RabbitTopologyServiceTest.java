package cl.duoc.pedidos360.usuario.service;

import cl.duoc.pedidos360.usuario.entity.RabbitBinding;
import cl.duoc.pedidos360.usuario.entity.RabbitExchange;
import cl.duoc.pedidos360.usuario.entity.RabbitQueue;
import cl.duoc.pedidos360.usuario.repository.RabbitBindingRepository;
import cl.duoc.pedidos360.usuario.repository.RabbitExchangeRepository;
import cl.duoc.pedidos360.usuario.repository.RabbitQueueRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RabbitTopologyServiceTest {

    @Mock
    private RabbitExchangeRepository exchangeRepository;

    @Mock
    private RabbitQueueRepository queueRepository;

    @Mock
    private RabbitBindingRepository bindingRepository;

    @Mock
    private RabbitAdminService adminService;

    @InjectMocks
    private RabbitTopologyService topologyService;

    @Test
    @DisplayName("resyncTopology declares all active exchanges, queues, and bindings")
    void testResyncTopologySuccess() {
        RabbitExchange exchange = new RabbitExchange(1L, "pedidos.events", "TOPIC", true, false, false, true, true);
        RabbitQueue queue = new RabbitQueue(1L, "notificacion.pedidos", true, false, false, null, null, true, true);
        RabbitBinding binding = new RabbitBinding(1L, queue, exchange, "pedido.*", true, true);

        when(exchangeRepository.findByActiveTrue()).thenReturn(List.of(exchange));
        when(queueRepository.findByActiveTrue()).thenReturn(List.of(queue));
        when(bindingRepository.findByActiveTrue()).thenReturn(List.of(binding));

        topologyService.resyncTopology();

        verify(adminService).declareExchangeInRabbitMQ(exchange);
        verify(adminService).declareQueueInRabbitMQ(queue);
        verify(adminService).declareBindingInRabbitMQ(binding);
    }

    @Test
    @DisplayName("onApplicationReady invokes resyncTopology")
    void testOnApplicationReady() {
        when(exchangeRepository.findByActiveTrue()).thenReturn(Collections.emptyList());
        when(queueRepository.findByActiveTrue()).thenReturn(Collections.emptyList());
        when(bindingRepository.findByActiveTrue()).thenReturn(Collections.emptyList());

        topologyService.onApplicationReady();

        verify(exchangeRepository).findByActiveTrue();
        verify(queueRepository).findByActiveTrue();
        verify(bindingRepository).findByActiveTrue();
    }

    @Test
    @DisplayName("resyncTopology handles broker exceptions gracefully without throwing")
    void testResyncTopologyHandlesExceptionGracefully() {
        when(exchangeRepository.findByActiveTrue()).thenThrow(new RuntimeException("Database error"));

        assertDoesNotThrow(() -> topologyService.resyncTopology());
    }

    @Test
    @DisplayName("resyncTopology handles individual declaration exception gracefully")
    void testResyncTopologyHandlesDeclarationException() {
        RabbitExchange exchange = new RabbitExchange(2L, "broken.exchange", "TOPIC", true, false, false, false, true);
        when(exchangeRepository.findByActiveTrue()).thenReturn(List.of(exchange));
        doThrow(new RuntimeException("Broker connection refused")).when(adminService).declareExchangeInRabbitMQ(exchange);

        assertDoesNotThrow(() -> topologyService.resyncTopology());
    }
}
