package cl.duoc.pedidos360.analitica.listener;

import cl.duoc.pedidos360.analitica.entity.AnaliticaEvento;
import cl.duoc.pedidos360.analitica.event.PedidoCreadoEvent;
import cl.duoc.pedidos360.analitica.kafka.consumer.AnaliticaKafkaConsumer;
import cl.duoc.pedidos360.analitica.service.AnaliticaService;
import cl.duoc.pedidos360.analitica.service.IdempotencyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnaliticaKafkaRabbitSimultaneousTest {

    @Mock
    private AnaliticaService analiticaService;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private Channel rabbitChannel;

    @Mock
    private Acknowledgment kafkaAck;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    private AnaliticaKafkaConsumer kafkaConsumer;
    private AnaliticaEventListener rabbitListener;

    private final Set<String> simulatedDb = ConcurrentHashMap.newKeySet();

    @BeforeEach
    void setUp() {
        kafkaConsumer = new AnaliticaKafkaConsumer(analiticaService, idempotencyService, objectMapper);
        rabbitListener = new AnaliticaEventListener(analiticaService, idempotencyService);

        // Simular base de datos persistente compartida
        when(idempotencyService.isAlreadyProcessed(anyString()))
                .thenAnswer(inv -> simulatedDb.contains(inv.getArgument(0)));

        doAnswer(inv -> {
            simulatedDb.add(inv.getArgument(0));
            return null;
        }).when(idempotencyService).markAsProcessed(anyString(), anyString(), anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("Simultaneidad: Si Kafka procesa primero, RabbitMQ descarta el duplicado sin duplicar métrica")
    void testKafkaFirstThenRabbit() throws IOException {
        String eventId = "evt-order-999";
        String kafkaJson = String.format("""
            {
                "eventId": "%s",
                "eventType": "pedido.creado",
                "timestamp": "2026-10-30T22:00:00Z",
                "correlationId": "corr-999",
                "resourceId": 999,
                "payload": {"usuarioId": 5, "total": 20000.0}
            }
            """, eventId);

        ConsumerRecord<String, String> kafkaRecord =
                new ConsumerRecord<>("pedidos360.pedidos.v1", 0, 10L, "999", kafkaJson);

        // 1. Llega evento por Apache Kafka
        kafkaConsumer.consume(kafkaRecord, kafkaAck);
        verify(analiticaService, times(1)).registrarEvento(any(AnaliticaEvento.class));
        verify(kafkaAck, times(1)).acknowledge();

        // 2. Llega el mismo evento de dominio por RabbitMQ
        PedidoCreadoEvent rabbitEvent = new PedidoCreadoEvent(eventId, "pedido.creado", "2026-10-30T22:00:00Z", 999L, 5L, 20000.0);

        rabbitListener.handlePedidoCreado(rabbitEvent, rabbitChannel, 1L);

        // Verificar que RabbitMQ envió ACK pero NO registró una segunda métrica en Analítica
        verify(rabbitChannel, times(1)).basicAck(1L, false);
        verify(analiticaService, times(1)).registrarEvento(any(AnaliticaEvento.class)); // Sigue siendo 1!
    }

    @Test
    @DisplayName("Simultaneidad: Si RabbitMQ procesa primero, Kafka descarta el duplicado sin duplicar métrica")
    void testRabbitFirstThenKafka() throws IOException {
        String eventId = "evt-order-888";

        // 1. Llega evento por RabbitMQ
        PedidoCreadoEvent rabbitEvent = new PedidoCreadoEvent(eventId, "pedido.creado", "2026-10-30T22:00:00Z", 888L, 7L, 45000.0);

        rabbitListener.handlePedidoCreado(rabbitEvent, rabbitChannel, 2L);
        verify(analiticaService, times(1)).registrarEvento(any(AnaliticaEvento.class));
        verify(rabbitChannel, times(1)).basicAck(2L, false);

        // 2. Llega el mismo evento por Apache Kafka
        String kafkaJson = String.format("""
            {
                "eventId": "%s",
                "eventType": "pedido.creado",
                "timestamp": "2026-10-30T22:00:00Z",
                "correlationId": "corr-888",
                "resourceId": 888,
                "payload": {"usuarioId": 7, "total": 45000.0}
            }
            """, eventId);

        ConsumerRecord<String, String> kafkaRecord =
                new ConsumerRecord<>("pedidos360.pedidos.v1", 0, 11L, "888", kafkaJson);

        kafkaConsumer.consume(kafkaRecord, kafkaAck);

        // Verificar que Kafka envió ACK pero NO registró una segunda métrica
        verify(kafkaAck, times(1)).acknowledge();
        verify(analiticaService, times(1)).registrarEvento(any(AnaliticaEvento.class)); // Sigue siendo 1!
    }
}
