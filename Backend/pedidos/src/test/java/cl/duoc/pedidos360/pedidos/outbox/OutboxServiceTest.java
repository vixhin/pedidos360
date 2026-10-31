package cl.duoc.pedidos360.pedidos.outbox;

import cl.duoc.pedidos360.pedidos.outbox.entity.OutboxEvent;
import cl.duoc.pedidos360.pedidos.outbox.entity.OutboxStatus;
import cl.duoc.pedidos360.pedidos.outbox.repository.OutboxEventRepository;
import cl.duoc.pedidos360.pedidos.outbox.service.OutboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @InjectMocks
    private OutboxService outboxService;

    private OutboxEvent mockEvent;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(outboxService, "maxRetries", 5);
        ReflectionTestUtils.setField(outboxService, "initialBackoffMs", 1000L);
        ReflectionTestUtils.setField(outboxService, "maxBackoffMs", 60000L);

        mockEvent = new OutboxEvent(
                "evt-123",
                "PEDIDO",
                "100",
                "pedido.creado",
                "pedidos360.pedidos.v1",
                "100",
                "corr-123",
                "{\"resourceId\":100}"
        );
        mockEvent.setId(1L);
    }

    @Test
    @DisplayName("saveEvent - guarda evento correctamente en estado PENDING")
    void testSaveEvent() {
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenReturn(mockEvent);

        OutboxEvent saved = outboxService.saveEvent(
                "evt-123", "PEDIDO", "100", "pedido.creado",
                "pedidos360.pedidos.v1", "100", "corr-123", "{\"resourceId\":100}"
        );

        assertThat(saved).isNotNull();
        assertThat(saved.getEventId()).isEqualTo("evt-123");
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDING);
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("reserveBatchForPublishing - reserva lote en estado PROCESSING con lockedBy y timestamp")
    void testReserveBatchForPublishing() {
        when(outboxEventRepository.findAndLockBatchForProcessing(any(), any(), anyInt()))
                .thenReturn(List.of(mockEvent));
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(i -> i.getArguments()[0]);

        List<OutboxEvent> reserved = outboxService.reserveBatchForPublishing("worker-1", 10, 300L);

        assertThat(reserved).hasSize(1);
        assertThat(reserved.get(0).getStatus()).isEqualTo(OutboxStatus.PROCESSING);
        assertThat(reserved.get(0).getLockedBy()).isEqualTo("worker-1");
        assertThat(reserved.get(0).getProcessingStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("markAsPublished - actualiza estado a PUBLISHED y libera lockedBy")
    void testMarkAsPublished() {
        mockEvent.setStatus(OutboxStatus.PROCESSING);
        mockEvent.setLockedBy("worker-1");
        when(outboxEventRepository.findById(1L)).thenReturn(Optional.of(mockEvent));

        outboxService.markAsPublished(1L);

        assertThat(mockEvent.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(mockEvent.getPublishedAt()).isNotNull();
        assertThat(mockEvent.getLockedBy()).isNull();
        verify(outboxEventRepository).save(mockEvent);
    }

    @Test
    @DisplayName("markAsFailed - reintento transitorio calcula backoff exponencial y mantiene PENDING")
    void testMarkAsFailedTemporary() {
        mockEvent.setRetryCount(1);
        when(outboxEventRepository.findById(1L)).thenReturn(Optional.of(mockEvent));

        outboxService.markAsFailed(1L, "Kafka timeout", false);

        assertThat(mockEvent.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(mockEvent.getRetryCount()).isEqualTo(2);
        assertThat(mockEvent.getNextRetryAt()).isNotNull();
        assertThat(mockEvent.getErrorType()).isEqualTo("TEMPORARY");
        verify(outboxEventRepository).save(mockEvent);
    }

    @Test
    @DisplayName("markAsFailed - error permanente marca FAILED inmediatamente")
    void testMarkAsFailedPermanent() {
        when(outboxEventRepository.findById(1L)).thenReturn(Optional.of(mockEvent));

        outboxService.markAsFailed(1L, "Serialization error", true);

        assertThat(mockEvent.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(mockEvent.getErrorType()).isEqualTo("PERMANENT");
        verify(outboxEventRepository).save(mockEvent);
    }

    @Test
    @DisplayName("reprocessFailedEvents - recupera eventos FAILED cambiándolos a PENDING con reintentos en 0")
    void testReprocessFailedEvents() {
        mockEvent.setStatus(OutboxStatus.FAILED);
        mockEvent.setRetryCount(5);
        mockEvent.setErrorMessage("Broker connection error");
        when(outboxEventRepository.findByStatus(OutboxStatus.FAILED)).thenReturn(List.of(mockEvent));

        int reprocessed = outboxService.reprocessFailedEvents(10);

        assertThat(reprocessed).isEqualTo(1);
        assertThat(mockEvent.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(mockEvent.getRetryCount()).isEqualTo(0);
        assertThat(mockEvent.getErrorMessage()).isNull();
        verify(outboxEventRepository).save(mockEvent);
    }
}
