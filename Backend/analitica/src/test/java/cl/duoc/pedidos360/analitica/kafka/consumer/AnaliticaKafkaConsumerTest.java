package cl.duoc.pedidos360.analitica.kafka.consumer;

import cl.duoc.pedidos360.analitica.entity.AnaliticaEvento;
import cl.duoc.pedidos360.analitica.entity.ProcessedEvent;
import cl.duoc.pedidos360.analitica.service.IdempotencyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnaliticaKafkaConsumerTest {

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private Acknowledgment acknowledgment;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    private AnaliticaKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AnaliticaKafkaConsumer(idempotencyService, objectMapper);
    }

    @Test
    @DisplayName("consume - procesa pedido.creado atómicamente y envía ACK")
    void testConsumePedidoCreado() {
        String json = """
            {
                "eventId": "evt-001",
                "eventType": "pedido.creado",
                "timestamp": "2026-10-30T22:00:00Z",
                "correlationId": "corr-001",
                "resourceId": 50,
                "payload": {
                    "usuarioId": 12,
                    "total": 35000.0,
                    "estado": "PENDIENTE"
                }
            }
            """;

        ConsumerRecord<String, String> record = new ConsumerRecord<>("pedidos360.pedidos.v1", 0, 100L, "50", json);
        when(idempotencyService.processEventTransactional(any(ProcessedEvent.class), any(AnaliticaEvento.class)))
                .thenReturn(true);

        consumer.consume(record, acknowledgment);

        ArgumentCaptor<ProcessedEvent> eventCaptor = ArgumentCaptor.forClass(ProcessedEvent.class);
        ArgumentCaptor<AnaliticaEvento> metricCaptor = ArgumentCaptor.forClass(AnaliticaEvento.class);

        verify(idempotencyService).processEventTransactional(eventCaptor.capture(), metricCaptor.capture());
        assertThat(eventCaptor.getValue().getEventId()).isEqualTo("evt-001");
        assertThat(eventCaptor.getValue().getSourceChannel()).isEqualTo("KAFKA");
        assertThat(metricCaptor.getValue().getTipoEvento()).isEqualTo("PEDIDO_CREADO");
        assertThat(metricCaptor.getValue().getDescripcion()).contains("Pedido #50").contains("35000");

        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("consume - procesa pedido.estado.actualizado correctamente")
    void testConsumePedidoEstadoActualizado() {
        String json = """
            {
                "eventId": "evt-002",
                "eventType": "pedido.estado.actualizado",
                "timestamp": "2026-10-30T22:05:00Z",
                "correlationId": "corr-002",
                "resourceId": 50,
                "payload": {
                    "usuarioId": 12,
                    "nuevoEstado": "EN_CAMINO",
                    "anteriorEstado": "EN_PREPARACION"
                }
            }
            """;

        ConsumerRecord<String, String> record = new ConsumerRecord<>("pedidos360.pedidos.v1", 0, 101L, "50", json);
        when(idempotencyService.processEventTransactional(any(ProcessedEvent.class), any(AnaliticaEvento.class)))
                .thenReturn(true);

        consumer.consume(record, acknowledgment);

        ArgumentCaptor<AnaliticaEvento> metricCaptor = ArgumentCaptor.forClass(AnaliticaEvento.class);
        verify(idempotencyService).processEventTransactional(any(ProcessedEvent.class), metricCaptor.capture());
        assertThat(metricCaptor.getValue().getTipoEvento()).isEqualTo("PEDIDO_ESTADO_ACTUALIZADO");
        assertThat(metricCaptor.getValue().getDescripcion()).contains("Pedido #50").contains("EN_CAMINO");

        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("consume - procesa pedido.repartidor.asignado correctamente")
    void testConsumeRepartidorAsignado() {
        String json = """
            {
                "eventId": "evt-003",
                "eventType": "pedido.repartidor.asignado",
                "timestamp": "2026-10-30T22:10:00Z",
                "correlationId": "corr-003",
                "resourceId": 50,
                "payload": {
                    "usuarioId": 12,
                    "repartidorId": 88,
                    "nombreRepartidor": "Juan Repartidor",
                    "nuevoEstado": "EN_CAMINO"
                }
            }
            """;

        ConsumerRecord<String, String> record = new ConsumerRecord<>("pedidos360.pedidos.v1", 0, 102L, "50", json);
        when(idempotencyService.processEventTransactional(any(ProcessedEvent.class), any(AnaliticaEvento.class)))
                .thenReturn(true);

        consumer.consume(record, acknowledgment);

        ArgumentCaptor<AnaliticaEvento> metricCaptor = ArgumentCaptor.forClass(AnaliticaEvento.class);
        verify(idempotencyService).processEventTransactional(any(ProcessedEvent.class), metricCaptor.capture());
        assertThat(metricCaptor.getValue().getTipoEvento()).isEqualTo("REPARTIDOR_ASIGNADO");
        assertThat(metricCaptor.getValue().getDescripcion()).contains("Juan Repartidor").contains("88");

        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("consume - reconoce (ACK) duplicado sin volver a registrar métrica")
    void testConsumeDuplicateEventIgnored() {
        String json = """
            {
                "eventId": "evt-duplicate",
                "eventType": "pedido.creado",
                "timestamp": "2026-10-30T22:00:00Z",
                "correlationId": "corr-001",
                "resourceId": 50,
                "payload": {"usuarioId": 12, "total": 35000.0}
            }
            """;

        ConsumerRecord<String, String> record = new ConsumerRecord<>("pedidos360.pedidos.v1", 0, 103L, "50", json);
        when(idempotencyService.processEventTransactional(any(ProcessedEvent.class), any(AnaliticaEvento.class)))
                .thenReturn(false); // Duplicado detectado por restricción UNIQUE

        consumer.consume(record, acknowledgment);

        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("consume - si ocurre error inesperado, lanza excepción para activar DefaultErrorHandler (reintentos/DLT)")
    void testConsumeErrorTriggersRetry() {
        String invalidJson = "{ malformed json }";
        ConsumerRecord<String, String> record = new ConsumerRecord<>("pedidos360.pedidos.v1", 0, 104L, "50", invalidJson);

        assertThrows(RuntimeException.class, () -> consumer.consume(record, acknowledgment));
        verify(acknowledgment, never()).acknowledge();
    }
}
