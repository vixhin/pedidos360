package cl.duoc.pedidos360.analitica.kafka.consumer;

import cl.duoc.pedidos360.analitica.entity.AnaliticaEvento;
import cl.duoc.pedidos360.analitica.service.AnaliticaService;
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
    private AnaliticaService analiticaService;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private Acknowledgment acknowledgment;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    private AnaliticaKafkaConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AnaliticaKafkaConsumer(analiticaService, idempotencyService, objectMapper);
    }

    @Test
    @DisplayName("consume - procesa pedido.creado, registra métrica, guarda idempotencia y envía ACK")
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
        when(idempotencyService.isAlreadyProcessed("evt-001")).thenReturn(false);

        consumer.consume(record, acknowledgment);

        ArgumentCaptor<AnaliticaEvento> captor = ArgumentCaptor.forClass(AnaliticaEvento.class);
        verify(analiticaService).registrarEvento(captor.capture());
        assertThat(captor.getValue().getTipoEvento()).isEqualTo("PEDIDO_CREADO");
        assertThat(captor.getValue().getDescripcion()).contains("Pedido #50").contains("usuarioId=12").contains("35000");

        verify(idempotencyService).markAsProcessed("evt-001", "KAFKA", "pedido.creado", "50", "corr-001", "analitica-group");
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
        when(idempotencyService.isAlreadyProcessed("evt-002")).thenReturn(false);

        consumer.consume(record, acknowledgment);

        ArgumentCaptor<AnaliticaEvento> captor = ArgumentCaptor.forClass(AnaliticaEvento.class);
        verify(analiticaService).registrarEvento(captor.capture());
        assertThat(captor.getValue().getTipoEvento()).isEqualTo("PEDIDO_ESTADO_ACTUALIZADO");
        assertThat(captor.getValue().getDescripcion()).contains("Pedido #50").contains("EN_CAMINO");

        verify(idempotencyService).markAsProcessed("evt-002", "KAFKA", "pedido.estado.actualizado", "50", "corr-002", "analitica-group");
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
        when(idempotencyService.isAlreadyProcessed("evt-003")).thenReturn(false);

        consumer.consume(record, acknowledgment);

        ArgumentCaptor<AnaliticaEvento> captor = ArgumentCaptor.forClass(AnaliticaEvento.class);
        verify(analiticaService).registrarEvento(captor.capture());
        assertThat(captor.getValue().getTipoEvento()).isEqualTo("REPARTIDOR_ASIGNADO");
        assertThat(captor.getValue().getDescripcion()).contains("Juan Repartidor").contains("88").contains("#50");

        verify(idempotencyService).markAsProcessed("evt-003", "KAFKA", "pedido.repartidor.asignado", "50", "corr-003", "analitica-group");
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("consume - descarta evento duplicado sin registrar métrica adicional y envía ACK")
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
        when(idempotencyService.isAlreadyProcessed("evt-duplicate")).thenReturn(true);

        consumer.consume(record, acknowledgment);

        verify(analiticaService, never()).registrarEvento(any());
        verify(idempotencyService, never()).markAsProcessed(any(), any(), any(), any(), any(), any());
        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("consume - si ocurre error inesperado, lanza excepción para activar reintentos/DLT")
    void testConsumeErrorTriggersRetry() {
        String invalidJson = "{ malformed json }";
        ConsumerRecord<String, String> record = new ConsumerRecord<>("pedidos360.pedidos.v1", 0, 104L, "50", invalidJson);

        assertThrows(RuntimeException.class, () -> consumer.consume(record, acknowledgment));
        verify(acknowledgment, never()).acknowledge();
    }
}

