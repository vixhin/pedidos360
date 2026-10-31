package cl.duoc.pedidos360.pedidos.outbox.publisher;

import cl.duoc.pedidos360.pedidos.outbox.entity.OutboxEvent;
import cl.duoc.pedidos360.pedidos.outbox.service.OutboxService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.kafka.common.errors.SerializationException;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
@EnableScheduling
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final String workerId = "publisher-" + UUID.randomUUID().toString().substring(0, 8);

    @Value("${kafka.enabled:false}")
    private boolean kafkaEnabled;

    @Value("${kafka.outbox.batch-size:50}")
    private int batchSize;

    @Value("${kafka.outbox.timeout-seconds:5}")
    private int timeoutSeconds;

    @Value("${kafka.outbox.stuck-timeout-seconds:300}")
    private long stuckTimeoutSeconds;

    private final OutboxService outboxService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    public OutboxPublisher(OutboxService outboxService,
                           @Autowired(required = false) KafkaTemplate<String, String> kafkaTemplate) {
        this(outboxService, kafkaTemplate, null);
    }

    @Autowired
    public OutboxPublisher(OutboxService outboxService,
                           @Autowired(required = false) KafkaTemplate<String, String> kafkaTemplate,
                           @Autowired(required = false) MeterRegistry meterRegistry) {
        this.outboxService = outboxService;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedDelayString = "${kafka.outbox.poll-interval-ms:2000}")
    public void processOutbox() {
        if (!kafkaEnabled || kafkaTemplate == null) {
            log.trace("[OUTBOX-PUBLISHER] Kafka disabled or KafkaTemplate unavailable. Skipping outbox publishing.");
            return;
        }

        List<OutboxEvent> reservedEvents;
        try {
            // 1. Reserva segura con FOR UPDATE SKIP LOCKED y token de reserva único (lease)
            reservedEvents = outboxService.reserveBatchForPublishing(workerId, batchSize, stuckTimeoutSeconds);
        } catch (Exception ex) {
            log.error("[OUTBOX-PUBLISHER] Error reserving outbox batch in PostgreSQL: {}", ex.getMessage());
            return;
        }

        if (reservedEvents.isEmpty()) {
            return;
        }

        log.debug("[OUTBOX-PUBLISHER] Worker {} secured lease for {} event(s)", workerId, reservedEvents.size());

        for (OutboxEvent event : reservedEvents) {
            long startTime = System.currentTimeMillis();
            try {
                log.info("[OUTBOX-PUBLISHER] Publishing event: id={} resId={} eventId={} topic={} key={}",
                        event.getId(), event.getReservationId(), event.getEventId(), event.getTopic(), event.getKafkaKey());

                CompletableFuture<SendResult<String, String>> future =
                        kafkaTemplate.send(event.getTopic(), event.getKafkaKey(), event.getPayload());

                // Esperar confirmación real (ACK) del broker Kafka
                SendResult<String, String> result = future.get(timeoutSeconds, TimeUnit.SECONDS);

                long duration = System.currentTimeMillis() - startTime;
                if (meterRegistry != null) {
                    Timer.builder("pedidos360.outbox.publish.duration")
                            .tag("topic", event.getTopic())
                            .register(meterRegistry)
                            .record(duration, TimeUnit.MILLISECONDS);
                }

                // 2. Validación atómica del token de reserva antes de marcar PUBLISHED
                boolean marked = outboxService.markAsPublished(event.getId(), event.getReservationId());
                if (!marked) {
                    log.warn("[OUTBOX-PUBLISHER] Kafka ACK received, but lease was lost for event id={} resId={}. At-least-once downstream idempotency will handle duplicate.",
                            event.getId(), event.getReservationId());
                } else {
                    log.info("[OUTBOX-PUBLISHER] Event successfully marked PUBLISHED: id={} offset={} durationMs={}",
                            event.getId(), result.getRecordMetadata().offset(), duration);
                }

            } catch (Exception ex) {
                String error = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
                boolean isPermanent = isPermanentError(ex);

                log.warn("[OUTBOX-PUBLISHER] Error publishing event id={} resId={} (permanent={}): {}",
                        event.getId(), event.getReservationId(), isPermanent, error);

                outboxService.markAsFailed(event.getId(), event.getReservationId(), error, isPermanent);
            }
        }
    }

    private boolean isPermanentError(Throwable ex) {
        if (ex instanceof SerializationException) {
            return true;
        }
        if (ex instanceof IllegalArgumentException) {
            return true;
        }
        if (ex.getCause() != null && ex.getCause() != ex) {
            return isPermanentError(ex.getCause());
        }
        return false;
    }
}
