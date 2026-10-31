package cl.duoc.pedidos360.pedidos.outbox;

import cl.duoc.pedidos360.pedidos.outbox.entity.OutboxEvent;
import cl.duoc.pedidos360.pedidos.outbox.publisher.OutboxPublisher;
import cl.duoc.pedidos360.pedidos.outbox.service.OutboxService;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxService outboxService;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxPublisher outboxPublisher;

    private OutboxEvent mockEvent;

    @BeforeEach
    void setUp() {
        outboxPublisher = new OutboxPublisher(outboxService, kafkaTemplate);
        ReflectionTestUtils.setField(outboxPublisher, "kafkaEnabled", true);

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
        mockEvent.setId(10L);
    }

    @Test
    @DisplayName("processOutbox - publica a Kafka y marca PUBLISHED al recibir ACK")
    void testProcessOutboxSuccess() throws Exception {
        when(outboxService.findPendingEvents()).thenReturn(List.of(mockEvent));

        RecordMetadata metadata = new RecordMetadata(new TopicPartition("pedidos360.pedidos.v1", 0), 0, 0, 0, 0, 0);
        SendResult<String, String> sendResult = new SendResult<>(null, metadata);
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(sendResult);

        when(kafkaTemplate.send(eq("pedidos360.pedidos.v1"), eq("100"), any())).thenReturn(future);

        outboxPublisher.processOutbox();

        verify(kafkaTemplate).send("pedidos360.pedidos.v1", "100", mockEvent.getPayload());
        verify(outboxService).markAsPublished(10L);
        verify(outboxService, never()).markAsFailed(anyLong(), anyString());
    }

    @Test
    @DisplayName("processOutbox - marca FAILED/reintento si Kafka falla o desconecta")
    void testProcessOutboxFailure() {
        when(outboxService.findPendingEvents()).thenReturn(List.of(mockEvent));

        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Connection refused to Kafka broker"));

        when(kafkaTemplate.send(any(), any(), any())).thenReturn(future);

        outboxPublisher.processOutbox();

        verify(outboxService).markAsFailed(eq(10L), contains("Connection refused"));
        verify(outboxService, never()).markAsPublished(10L);
    }

    @Test
    @DisplayName("processOutbox - si kafkaEnabled es false, no intenta publicar nada")
    void testProcessOutboxDisabled() {
        ReflectionTestUtils.setField(outboxPublisher, "kafkaEnabled", false);

        outboxPublisher.processOutbox();

        verify(outboxService, never()).findPendingEvents();
        verifyNoInteractions(kafkaTemplate);
    }
}

