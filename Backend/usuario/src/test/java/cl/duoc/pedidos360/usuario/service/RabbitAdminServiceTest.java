package cl.duoc.pedidos360.usuario.service;

import cl.duoc.pedidos360.usuario.dto.*;
import cl.duoc.pedidos360.usuario.entity.*;
import cl.duoc.pedidos360.usuario.exception.ProtectedResourceException;
import cl.duoc.pedidos360.usuario.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitAdmin;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RabbitAdminServiceTest {

    @Mock
    private RabbitExchangeRepository exchangeRepository;

    @Mock
    private RabbitQueueRepository queueRepository;

    @Mock
    private RabbitBindingRepository bindingRepository;

    @Mock
    private RabbitAdmin rabbitAdmin;

    private RabbitAdminService adminService;

    @BeforeEach
    void setUp() {
        adminService = new RabbitAdminService(exchangeRepository, queueRepository, bindingRepository, rabbitAdmin);
    }

    // =========================================================================
    // EXCHANGE TESTS
    // =========================================================================

    @Nested
    @DisplayName("Exchanges")
    class ExchangeTests {

        @Test
        @DisplayName("Listar exchanges retorna todos los exchanges")
        void listExchanges_retornaTodos() {
            RabbitExchange e1 = new RabbitExchange(1L, "pedidos360.events", "TOPIC", true, false, false, true, true);
            RabbitExchange e2 = new RabbitExchange(2L, "pedidos360.dlx", "TOPIC", true, false, false, true, true);
            when(exchangeRepository.findAll()).thenReturn(List.of(e1, e2));

            List<RabbitExchangeResponseDTO> result = adminService.listExchanges();

            assertEquals(2, result.size());
            assertEquals("pedidos360.events", result.get(0).getName());
            assertEquals("pedidos360.dlx", result.get(1).getName());
        }

        @Test
        @DisplayName("Crear exchange exitosamente lo declara en RabbitMQ")
        void createExchange_exitosamente() {
            RabbitExchangeDTO dto = new RabbitExchangeDTO();
            dto.setName("test.exchange");
            dto.setType("TOPIC");

            when(exchangeRepository.existsByName("test.exchange")).thenReturn(false);
            when(exchangeRepository.save(any(RabbitExchange.class))).thenAnswer(inv -> {
                RabbitExchange e = inv.getArgument(0);
                e.setId(99L);
                return e;
            });

            RabbitExchangeResponseDTO result = adminService.createExchange(dto);

            assertEquals("test.exchange", result.getName());
            assertEquals("TOPIC", result.getType());
            verify(rabbitAdmin).declareExchange(any());
        }

        @Test
        @DisplayName("Crear exchange duplicado lanza IllegalArgumentException")
        void createExchange_duplicado() {
            RabbitExchangeDTO dto = new RabbitExchangeDTO();
            dto.setName("pedidos360.events");
            dto.setType("TOPIC");

            when(exchangeRepository.existsByName("pedidos360.events")).thenReturn(true);

            assertThrows(IllegalArgumentException.class, () -> adminService.createExchange(dto));
            verify(exchangeRepository, never()).save(any());
        }

        @Test
        @DisplayName("Eliminar exchange protegido lanza ProtectedResourceException")
        void deleteExchange_protegido() {
            RabbitExchange exchange = new RabbitExchange(1L, "pedidos360.events", "TOPIC", true, false, false, true, true);
            when(exchangeRepository.findById(1L)).thenReturn(Optional.of(exchange));

            assertThrows(ProtectedResourceException.class, () -> adminService.deleteExchange(1L));
            verify(rabbitAdmin, never()).deleteExchange(anyString());
            verify(exchangeRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Eliminar exchange no protegido lo elimina de DB y broker")
        void deleteExchange_noProtegido() {
            RabbitExchange exchange = new RabbitExchange(99L, "temp.exchange", "TOPIC", true, false, false, false, true);
            when(exchangeRepository.findById(99L)).thenReturn(Optional.of(exchange));

            adminService.deleteExchange(99L);

            verify(rabbitAdmin).deleteExchange("temp.exchange");
            verify(exchangeRepository).delete(exchange);
        }

        @Test
        @DisplayName("Obtener exchange por ID inexistente lanza IllegalArgumentException")
        void getExchangeById_inexistente() {
            when(exchangeRepository.findById(999L)).thenReturn(Optional.empty());
            assertThrows(IllegalArgumentException.class, () -> adminService.getExchangeById(999L));
        }

        @Test
        @DisplayName("Desactivar exchange protegido lanza ProtectedResourceException")
        void updateExchange_desactivarProtegido() {
            RabbitExchange exchange = new RabbitExchange(1L, "pedidos360.events", "TOPIC", true, false, false, true, true);
            when(exchangeRepository.findById(1L)).thenReturn(Optional.of(exchange));

            RabbitExchangeDTO dto = new RabbitExchangeDTO();
            dto.setActive(false);

            assertThrows(ProtectedResourceException.class, () -> adminService.updateExchange(1L, dto));
        }
    }

    // =========================================================================
    // QUEUE TESTS
    // =========================================================================

    @Nested
    @DisplayName("Queues")
    class QueueTests {

        @Test
        @DisplayName("Listar queues retorna todas las queues")
        void listQueues_retornaTodas() {
            RabbitQueue q1 = new RabbitQueue(1L, "cart.queue", true, false, false, null, null, true, true);
            RabbitQueue q2 = new RabbitQueue(2L, "notifications.queue", true, false, false, null, null, true, true);
            when(queueRepository.findAll()).thenReturn(List.of(q1, q2));

            List<RabbitQueueResponseDTO> result = adminService.listQueues();

            assertEquals(2, result.size());
            assertEquals("cart.queue", result.get(0).getName());
        }

        @Test
        @DisplayName("Crear queue exitosamente la declara en RabbitMQ")
        void createQueue_exitosamente() {
            RabbitQueueDTO dto = new RabbitQueueDTO();
            dto.setName("test.queue");

            when(queueRepository.existsByName("test.queue")).thenReturn(false);
            when(queueRepository.save(any(RabbitQueue.class))).thenAnswer(inv -> {
                RabbitQueue q = inv.getArgument(0);
                q.setId(99L);
                return q;
            });

            RabbitQueueResponseDTO result = adminService.createQueue(dto);

            assertEquals("test.queue", result.getName());
            verify(rabbitAdmin).declareQueue(any());
        }

        @Test
        @DisplayName("Crear queue con DLX asocia la dead letter exchange")
        void createQueue_conDlx() {
            RabbitExchange dlx = new RabbitExchange(1L, "pedidos360.dlx", "TOPIC", true, false, false, true, true);

            RabbitQueueDTO dto = new RabbitQueueDTO();
            dto.setName("test.queue.dlx");
            dto.setDeadLetterExchangeId(1L);
            dto.setDeadLetterRoutingKey("test.dead-letter");

            when(queueRepository.existsByName("test.queue.dlx")).thenReturn(false);
            when(exchangeRepository.findById(1L)).thenReturn(Optional.of(dlx));
            when(queueRepository.save(any(RabbitQueue.class))).thenAnswer(inv -> {
                RabbitQueue q = inv.getArgument(0);
                q.setId(99L);
                return q;
            });

            RabbitQueueResponseDTO result = adminService.createQueue(dto);

            assertEquals("test.queue.dlx", result.getName());
            assertEquals(1L, result.getDeadLetterExchangeId());
            assertEquals("pedidos360.dlx", result.getDeadLetterExchangeName());
        }

        @Test
        @DisplayName("Crear queue duplicada lanza IllegalArgumentException")
        void createQueue_duplicada() {
            RabbitQueueDTO dto = new RabbitQueueDTO();
            dto.setName("cart.queue");

            when(queueRepository.existsByName("cart.queue")).thenReturn(true);

            assertThrows(IllegalArgumentException.class, () -> adminService.createQueue(dto));
        }

        @Test
        @DisplayName("Eliminar queue protegida lanza ProtectedResourceException")
        void deleteQueue_protegida() {
            RabbitQueue queue = new RabbitQueue(1L, "cart.queue", true, false, false, null, null, true, true);
            when(queueRepository.findById(1L)).thenReturn(Optional.of(queue));

            assertThrows(ProtectedResourceException.class, () -> adminService.deleteQueue(1L));
            verify(rabbitAdmin, never()).deleteQueue(anyString());
        }

        @Test
        @DisplayName("Eliminar queue no protegida la elimina de DB y broker")
        void deleteQueue_noProtegida() {
            RabbitQueue queue = new RabbitQueue(99L, "temp.queue", true, false, false, null, null, false, true);
            when(queueRepository.findById(99L)).thenReturn(Optional.of(queue));

            adminService.deleteQueue(99L);

            verify(rabbitAdmin).deleteQueue("temp.queue");
            verify(queueRepository).delete(queue);
        }
    }

    // =========================================================================
    // BINDING TESTS
    // =========================================================================

    @Nested
    @DisplayName("Bindings")
    class BindingTests {

        @Test
        @DisplayName("Crear binding exitosamente lo declara en RabbitMQ")
        void createBinding_exitosamente() {
            RabbitQueue queue = new RabbitQueue(1L, "test.queue", true, false, false, null, null, false, true);
            RabbitExchange exchange = new RabbitExchange(1L, "test.exchange", "TOPIC", true, false, false, false, true);

            RabbitBindingDTO dto = new RabbitBindingDTO();
            dto.setQueueId(1L);
            dto.setExchangeId(1L);
            dto.setRoutingKey("test.key");

            when(queueRepository.findById(1L)).thenReturn(Optional.of(queue));
            when(exchangeRepository.findById(1L)).thenReturn(Optional.of(exchange));
            when(bindingRepository.existsByQueue_IdAndExchange_IdAndRoutingKey(1L, 1L, "test.key")).thenReturn(false);
            when(bindingRepository.save(any(RabbitBinding.class))).thenAnswer(inv -> {
                RabbitBinding b = inv.getArgument(0);
                b.setId(99L);
                return b;
            });

            RabbitBindingResponseDTO result = adminService.createBinding(dto);

            assertEquals("test.queue", result.getQueueName());
            assertEquals("test.exchange", result.getExchangeName());
            assertEquals("test.key", result.getRoutingKey());
            verify(rabbitAdmin).declareBinding(any());
        }

        @Test
        @DisplayName("Crear binding duplicado lanza IllegalArgumentException")
        void createBinding_duplicado() {
            RabbitQueue queue = new RabbitQueue(1L, "test.queue", true, false, false, null, null, false, true);
            RabbitExchange exchange = new RabbitExchange(1L, "test.exchange", "TOPIC", true, false, false, false, true);

            RabbitBindingDTO dto = new RabbitBindingDTO();
            dto.setQueueId(1L);
            dto.setExchangeId(1L);
            dto.setRoutingKey("test.key");

            when(queueRepository.findById(1L)).thenReturn(Optional.of(queue));
            when(exchangeRepository.findById(1L)).thenReturn(Optional.of(exchange));
            when(bindingRepository.existsByQueue_IdAndExchange_IdAndRoutingKey(1L, 1L, "test.key")).thenReturn(true);

            assertThrows(IllegalArgumentException.class, () -> adminService.createBinding(dto));
        }

        @Test
        @DisplayName("Eliminar binding protegido lanza ProtectedResourceException")
        void deleteBinding_protegido() {
            RabbitQueue queue = new RabbitQueue(1L, "test.queue", true, false, false, null, null, false, true);
            RabbitExchange exchange = new RabbitExchange(1L, "test.exchange", "TOPIC", true, false, false, false, true);
            RabbitBinding binding = new RabbitBinding(1L, queue, exchange, "test.key", true, true);
            when(bindingRepository.findById(1L)).thenReturn(Optional.of(binding));

            assertThrows(ProtectedResourceException.class, () -> adminService.deleteBinding(1L));
            verify(rabbitAdmin, never()).removeBinding(any());
        }

        @Test
        @DisplayName("Eliminar binding no protegido lo elimina de DB y broker")
        void deleteBinding_noProtegido() {
            RabbitQueue queue = new RabbitQueue(1L, "test.queue", true, false, false, null, null, false, true);
            RabbitExchange exchange = new RabbitExchange(1L, "test.exchange", "TOPIC", true, false, false, false, true);
            RabbitBinding binding = new RabbitBinding(99L, queue, exchange, "test.key", false, true);
            when(bindingRepository.findById(99L)).thenReturn(Optional.of(binding));

            adminService.deleteBinding(99L);

            verify(rabbitAdmin).removeBinding(any());
            verify(bindingRepository).delete(binding);
        }

        @Test
        @DisplayName("Desactivar binding protegido lanza ProtectedResourceException")
        void updateBinding_desactivarProtegido() {
            RabbitQueue queue = new RabbitQueue(1L, "test.queue", true, false, false, null, null, false, true);
            RabbitExchange exchange = new RabbitExchange(1L, "test.exchange", "TOPIC", true, false, false, false, true);
            RabbitBinding binding = new RabbitBinding(1L, queue, exchange, "test.key", true, true);
            when(bindingRepository.findById(1L)).thenReturn(Optional.of(binding));

            RabbitBindingDTO dto = new RabbitBindingDTO();
            dto.setActive(false);

            assertThrows(ProtectedResourceException.class, () -> adminService.updateBinding(1L, dto));
        }
    }

    // =========================================================================
    // RESILIENCIA - Error en broker no afecta la integridad
    // =========================================================================

    @Nested
    @DisplayName("Resiliencia del broker")
    class ResilienciaTests {

        @Test
        @DisplayName("Error al declarar exchange en broker revierte la creacion en DB")
        void createExchange_errorBrokerRevierteDB() {
            RabbitExchangeDTO dto = new RabbitExchangeDTO();
            dto.setName("fail.exchange");
            dto.setType("TOPIC");

            when(exchangeRepository.existsByName("fail.exchange")).thenReturn(false);
            when(exchangeRepository.save(any(RabbitExchange.class))).thenAnswer(inv -> {
                RabbitExchange e = inv.getArgument(0);
                e.setId(99L);
                return e;
            });
            doThrow(new RuntimeException("Broker unreachable")).when(rabbitAdmin).declareExchange(any());

            assertThrows(IllegalStateException.class, () -> adminService.createExchange(dto));
            verify(exchangeRepository).delete(any(RabbitExchange.class));
        }

        @Test
        @DisplayName("Error al declarar queue en broker revierte la creacion en DB")
        void createQueue_errorBrokerRevierteDB() {
            RabbitQueueDTO dto = new RabbitQueueDTO();
            dto.setName("fail.queue");

            when(queueRepository.existsByName("fail.queue")).thenReturn(false);
            when(queueRepository.save(any(RabbitQueue.class))).thenAnswer(inv -> {
                RabbitQueue q = inv.getArgument(0);
                q.setId(99L);
                return q;
            });
            doThrow(new RuntimeException("Broker unreachable")).when(rabbitAdmin).declareQueue(any());

            assertThrows(IllegalStateException.class, () -> adminService.createQueue(dto));
            verify(queueRepository).delete(any(RabbitQueue.class));
        }

        @Test
        @DisplayName("Error al declarar binding en broker revierte la creacion en DB")
        void createBinding_errorBrokerRevierteDB() {
            RabbitQueue queue = new RabbitQueue(1L, "test.queue", true, false, false, null, null, false, true);
            RabbitExchange exchange = new RabbitExchange(1L, "test.exchange", "TOPIC", true, false, false, false, true);

            RabbitBindingDTO dto = new RabbitBindingDTO();
            dto.setQueueId(1L);
            dto.setExchangeId(1L);
            dto.setRoutingKey("test.key");

            when(queueRepository.findById(1L)).thenReturn(Optional.of(queue));
            when(exchangeRepository.findById(1L)).thenReturn(Optional.of(exchange));
            when(bindingRepository.existsByQueue_IdAndExchange_IdAndRoutingKey(1L, 1L, "test.key")).thenReturn(false);
            when(bindingRepository.save(any(RabbitBinding.class))).thenAnswer(inv -> {
                RabbitBinding b = inv.getArgument(0);
                b.setId(99L);
                return b;
            });
            doThrow(new RuntimeException("Broker unreachable")).when(rabbitAdmin).declareBinding(any());

            assertThrows(IllegalStateException.class, () -> adminService.createBinding(dto));
            verify(bindingRepository).delete(any(RabbitBinding.class));
        }

        @Test
        @DisplayName("Error al eliminar exchange del broker no impide la eliminacion de DB")
        void deleteExchange_errorBrokerNoImpideDB() {
            RabbitExchange exchange = new RabbitExchange(99L, "temp.exchange", "TOPIC", true, false, false, false, true);
            when(exchangeRepository.findById(99L)).thenReturn(Optional.of(exchange));
            doThrow(new RuntimeException("Broker down")).when(rabbitAdmin).deleteExchange("temp.exchange");

            adminService.deleteExchange(99L);

            // Se elimina de DB a pesar del error en broker
            verify(exchangeRepository).delete(exchange);
        }
    }
}
