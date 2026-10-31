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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    @DisplayName("reserveBatchForPublishing - reserva lote en estado PROCESSING con token único de reserva (reservationId)")
    void testReserveBatchForPublishing() {
        when(outboxEventRepository.findAndLockBatchForProcessing(any(), any(), anyInt()))
                .thenReturn(List.of(mockEvent));
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(i -> i.getArguments()[0]);

        List<OutboxEvent> reserved = outboxService.reserveBatchForPublishing("worker-1", 10, 300L);

        assertThat(reserved).hasSize(1);
        OutboxEvent event = reserved.get(0);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
        assertThat(event.getLockedBy()).isEqualTo("worker-1");
        assertThat(event.getReservationId()).isNotNull().isNotBlank();
        assertThat(event.getProcessingStartedAt()).isNotNull();
    }

    @Test
    @DisplayName("markAsPublished - actualiza condicionalmente si el reservationId es válido")
    void testMarkAsPublishedWithValidReservation() {
        when(outboxEventRepository.markPublishedConditional(eq(1L), eq("token-res-1"), any(Instant.class)))
                .thenReturn(1);

        boolean marked = outboxService.markAsPublished(1L, "token-res-1");

        assertThat(marked).isTrue();
        verify(outboxEventRepository).markPublishedConditional(eq(1L), eq("token-res-1"), any(Instant.class));
    }

    @Test
    @DisplayName("Escenario obligatorio: Publicador A con reserva vencida es rechazado si Publicador B tomó el lease")
    void testLateAckFromPublisherAIsRejectedWhenPublisherBReclaimedLease() {
        String reservationA = "lease-token-A";
        String reservationB = "lease-token-B";

        // Simular que el evento en base de datos ya pertenece al lease-token-B
        // Si publicador A intenta marcar con lease-token-A, rows affected = 0
        when(outboxEventRepository.markPublishedConditional(eq(1L), eq(reservationA), any(Instant.class)))
                .thenReturn(0);
        // Cuando publicador B intenta marcar con lease-token-B, rows affected = 1
        when(outboxEventRepository.markPublishedConditional(eq(1L), eq(reservationB), any(Instant.class)))
                .thenReturn(1);

        // 1. Publicador A intenta marcar PUBLISHED con su reserva vieja
        boolean markedByA = outboxService.markAsPublished(1L, reservationA);
        assertThat(markedByA).isFalse(); // Rechazado: A perdió la reserva

        // 2. Publicador B completa su operación con la reserva legítima
        boolean markedByB = outboxService.markAsPublished(1L, reservationB);
        assertThat(markedByB).isTrue(); // Aceptado: B es el dueño actual
    }

    @Test
    @DisplayName("markAsFailed - rechaza registrar fallo si el reservationId no coincide")
    void testMarkAsFailedRejectsMismatchedReservation() {
        mockEvent.setStatus(OutboxStatus.PROCESSING);
        mockEvent.setReservationId("token-active");
        when(outboxEventRepository.findById(1L)).thenReturn(Optional.of(mockEvent));

        boolean marked = outboxService.markAsFailed(1L, "token-expired", "Network timeout", false);

        assertThat(marked).isFalse();
        verify(outboxEventRepository, never()).markFailedConditional(any(), any(), any(), anyInt(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("markAsFailed - reintento transitorio calcula backoff y programa PENDING")
    void testMarkAsFailedTemporary() {
        mockEvent.setStatus(OutboxStatus.PROCESSING);
        mockEvent.setReservationId("token-1");
        mockEvent.setRetryCount(1);
        when(outboxEventRepository.findById(1L)).thenReturn(Optional.of(mockEvent));
        when(outboxEventRepository.markFailedConditional(eq(1L), eq("token-1"), eq(OutboxStatus.PENDING), eq(2), any(), any(), eq("TEMPORARY"), any()))
                .thenReturn(1);

        boolean failed = outboxService.markAsFailed(1L, "token-1", "Kafka timeout", false);

        assertThat(failed).isTrue();
    }

    @Test
    @DisplayName("markAsFailed - error permanente marca FAILED inmediatamente")
    void testMarkAsFailedPermanent() {
        mockEvent.setStatus(OutboxStatus.PROCESSING);
        mockEvent.setReservationId("token-1");
        when(outboxEventRepository.findById(1L)).thenReturn(Optional.of(mockEvent));
        when(outboxEventRepository.markFailedConditional(eq(1L), eq("token-1"), eq(OutboxStatus.FAILED), eq(1), any(), any(), eq("PERMANENT"), any()))
                .thenReturn(1);

        boolean failed = outboxService.markAsFailed(1L, "token-1", "Serialization error", true);

        assertThat(failed).isTrue();
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
