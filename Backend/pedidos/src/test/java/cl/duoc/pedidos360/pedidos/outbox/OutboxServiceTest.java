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
    @DisplayName("markAsPublished - actualiza estado a PUBLISHED y asigna publishedAt")
    void testMarkAsPublished() {
        when(outboxEventRepository.findById(1L)).thenReturn(Optional.of(mockEvent));

        outboxService.markAsPublished(1L);

        assertThat(mockEvent.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(mockEvent.getPublishedAt()).isNotNull();
        verify(outboxEventRepository).save(mockEvent);
    }

    @Test
    @DisplayName("markAsFailed - incrementa retryCount y marca FAILED al superar max retries")
    void testMarkAsFailedMaxRetries() {
        mockEvent.setRetryCount(4); // Siguiente intento será 5 (MAX_RETRIES)
        when(outboxEventRepository.findById(1L)).thenReturn(Optional.of(mockEvent));

        outboxService.markAsFailed(1L, "Kafka broker timeout");

        assertThat(mockEvent.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(mockEvent.getRetryCount()).isEqualTo(5);
        assertThat(mockEvent.getErrorMessage()).isEqualTo("Kafka broker timeout");
        verify(outboxEventRepository).save(mockEvent);
    }

    @Test
    @DisplayName("findPendingEvents - retorna lista de eventos en estado PENDING")
    void testFindPendingEvents() {
        when(outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(mockEvent));

        List<OutboxEvent> result = outboxService.findPendingEvents();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(OutboxStatus.PENDING);
    }
}

