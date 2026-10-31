package cl.duoc.pedidos360.analitica.kafka.consumer;

import cl.duoc.pedidos360.analitica.entity.AnaliticaEvento;
import cl.duoc.pedidos360.analitica.entity.ProcessedEvent;
import cl.duoc.pedidos360.analitica.service.AnaliticaService;
import cl.duoc.pedidos360.analitica.service.IdempotencyService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.support.Acknowledgment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnaliticaDltConsumerTest {

    @Mock
    private AnaliticaService analiticaService;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private Acknowledgment acknowledgment;

    private AnaliticaDltConsumer dltConsumer;

    @BeforeEach
    void setUp() {
        dltConsumer = new AnaliticaDltConsumer(analiticaService, idempotencyService);
    }

    @Test
    @DisplayName("consumeDlt - procesa mensaje DLT, registra en PostgreSQL y confirma con ACK")
    void testConsumeDltWithIdempotencySuccess() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "pedidos360.pedidos.dlt.v1", 0, 99L, "pedido-failed-id", "{\"error\": \"fatal\"}"
        );
        when(idempotencyService.processEventTransactional(any(ProcessedEvent.class), any(AnaliticaEvento.class)))
                .thenReturn(true);

        dltConsumer.consumeDlt(record, acknowledgment);

        ArgumentCaptor<ProcessedEvent> eventCaptor = ArgumentCaptor.forClass(ProcessedEvent.class);
        ArgumentCaptor<AnaliticaEvento> metricCaptor = ArgumentCaptor.forClass(AnaliticaEvento.class);

        verify(idempotencyService).processEventTransactional(eventCaptor.capture(), metricCaptor.capture());
        assertThat(eventCaptor.getValue().getEventId()).isEqualTo("dlt-pedidos360.pedidos.dlt.v1-0-99");
        assertThat(eventCaptor.getValue().getSourceChannel()).isEqualTo("DLT");
        assertThat(metricCaptor.getValue().getTipoEvento()).isEqualTo("ERROR_DLT");
        assertThat(metricCaptor.getValue().getDescripcion()).contains("pedidos360.pedidos.dlt.v1").contains("pedido-failed-id");

        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("consumeDlt - ignora mensaje DLT duplicado sin duplicar métrica y envía ACK")
    void testConsumeDltDuplicateIgnored() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "pedidos360.pedidos.dlt.v1", 0, 100L, "pedido-dup-id", "{\"error\": \"timeout\"}"
        );
        when(idempotencyService.processEventTransactional(any(ProcessedEvent.class), any(AnaliticaEvento.class)))
                .thenReturn(false); // Duplicado detectado

        dltConsumer.consumeDlt(record, acknowledgment);

        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("consumeDlt - ante fallo de base de datos lanza excepción y NUNCA envía ACK")
    void testConsumeDltPersistenceFailureThrowsAndNoAck() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "pedidos360.pedidos.dlt.v1", 0, 101L, "pedido-db-fail", "{\"error\": \"poison\"}"
        );
        when(idempotencyService.processEventTransactional(any(ProcessedEvent.class), any(AnaliticaEvento.class)))
                .thenThrow(new DataAccessResourceFailureException("PostgreSQL connection lost"));

        assertThrows(RuntimeException.class, () -> dltConsumer.consumeDlt(record, acknowledgment));

        // El mensaje NO debe confirmarse para que el broker mantenga el offset y se reintente
        verify(acknowledgment, never()).acknowledge();
    }
}
