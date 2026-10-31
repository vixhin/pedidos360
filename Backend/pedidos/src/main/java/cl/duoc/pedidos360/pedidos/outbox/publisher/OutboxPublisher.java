package cl.duoc.pedidos360.pedidos.outbox.publisher;

import cl.duoc.pedidos360.pedidos.outbox.entity.OutboxEvent;
import cl.duoc.pedidos360.pedidos.outbox.service.OutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
@EnableScheduling
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    @Value("${kafka.enabled:false}")
    private boolean kafkaEnabled;

    private final OutboxService outboxService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(OutboxService outboxService,
                           @Autowired(required = false) KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxService = outboxService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${kafka.outbox.poll-interval-ms:2000}")
    public void processOutbox() {
        if (!kafkaEnabled || kafkaTemplate == null) {
            log.trace("[OUTBOX-PUBLISHER] Kafka disabled or KafkaTemplate unavailable. Skipping outbox publishing.");
            return;
        }

        List<OutboxEvent> pendingEvents = outboxService.findPendingEvents();
        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("[OUTBOX-PUBLISHER] Processing {} pending outbox event(s)", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                log.info("[OUTBOX-PUBLISHER] Publishing event to Kafka: id={} eventId={} topic={} key={}",
                        event.getId(), event.getEventId(), event.getTopic(), event.getKafkaKey());

                CompletableFuture<SendResult<String, String>> future =
                        kafkaTemplate.send(event.getTopic(), event.getKafkaKey(), event.getPayload());

                // Esperar confirmación síncrona con timeout controlado para garantizar ACK estricto
                SendResult<String, String> result = future.get(5, TimeUnit.SECONDS);

                outboxService.markAsPublished(event.getId());

                log.info("[OUTBOX-PUBLISHER] Kafka ACK received: id={} eventId={} topic={} partition={} offset={}",
                        event.getId(),
                        event.getEventId(),
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());

            } catch (Exception ex) {
                String error = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
                log.warn("[OUTBOX-PUBLISHER] Failed to publish event id={} to Kafka: {}", event.getId(), error);
                outboxService.markAsFailed(event.getId(), error);
            }
        }
    }
}

