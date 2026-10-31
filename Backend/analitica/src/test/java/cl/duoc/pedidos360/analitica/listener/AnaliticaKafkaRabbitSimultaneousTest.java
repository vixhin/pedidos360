package cl.duoc.pedidos360.analitica.listener;

import cl.duoc.pedidos360.analitica.entity.AnaliticaEvento;
import cl.duoc.pedidos360.analitica.entity.ProcessedEvent;
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
        kafkaConsumer = new AnaliticaKafkaConsumer(idempotencyService, objectMapper);
        rabbitListener = new AnaliticaEventListener(analiticaService, idempotencyService);

        // Simular base de datos persistente compartida con restricción UNIQUE atómica
        when(idempotencyService.processEventTransactional(any(ProcessedEvent.class), any(AnaliticaEvento.class)))
                .thenAnswer(inv -> {
                    ProcessedEvent event = inv.getArgument(0);
                    AnaliticaEvento metric = inv.getArgument(1);
                    boolean isNew = simulatedDb.add(event.getEventId());
                    if (isNew && metric != null) {
                        analiticaService.registrarEvento(metric);
                    }
                    return isNew;
                });
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
                "payload": {"usuarioId": 5, "total": 45000.0, "estado": "PENDIENTE"}
            }
            """, eventId);

        ConsumerRecord<String, String> kafkaRecord =
                new ConsumerRecord<>("pedidos360.pedidos.v1", 0, 10L, "999", kafkaJson);

        // 1. Entrega por Kafka primero
        kafkaConsumer.consume(kafkaRecord, kafkaAck);
        verify(kafkaAck).acknowledge();

        // 2. Entrega por RabbitMQ del mismo eventId posteriormente
        PedidoCreadoEvent rabbitEvent = new PedidoCreadoEvent(eventId, "pedido.creado", "2026-10-30T22:00:00Z", 999L, 5L, 45000.0);
        rabbitListener.handlePedidoCreado(rabbitEvent, rabbitChannel, 101L);

        // Verificar que Rabbit envía ACK pero no duplica la métrica en Analitica
        verify(rabbitChannel).basicAck(101L, false);
        verify(analiticaService, times(1)).registrarEvento(any(AnaliticaEvento.class));
    }

    @Test
    @DisplayName("Simultaneidad: Si RabbitMQ procesa primero, Kafka descarta el duplicado sin duplicar métrica")
    void testRabbitFirstThenKafka() throws IOException {
        String eventId = "evt-order-888";

        // 1. Entrega por RabbitMQ primero
        PedidoCreadoEvent rabbitEvent = new PedidoCreadoEvent(eventId, "pedido.creado", "2026-10-30T22:00:00Z", 888L, 7L, 20000.0);
        rabbitListener.handlePedidoCreado(rabbitEvent, rabbitChannel, 201L);

        verify(rabbitChannel).basicAck(201L, false);

        // 2. Entrega por Kafka del mismo eventId posteriormente
        String kafkaJson = String.format("""
            {
                "eventId": "%s",
                "eventType": "pedido.creado",
                "timestamp": "2026-10-30T22:00:00Z",
                "correlationId": "corr-888",
                "resourceId": 888,
                "payload": {"usuarioId": 7, "total": 20000.0, "estado": "PENDIENTE"}
            }
            """, eventId);

        ConsumerRecord<String, String> kafkaRecord =
                new ConsumerRecord<>("pedidos360.pedidos.v1", 0, 20L, "888", kafkaJson);

        kafkaConsumer.consume(kafkaRecord, kafkaAck);
        verify(kafkaAck).acknowledge();

        // Total de métricas registradas en analitica debe ser exactamente 1
        verify(analiticaService, times(1)).registrarEvento(any(AnaliticaEvento.class));
    }
}
