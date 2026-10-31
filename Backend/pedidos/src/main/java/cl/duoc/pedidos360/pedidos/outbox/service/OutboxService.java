package cl.duoc.pedidos360.pedidos.outbox.service;

import cl.duoc.pedidos360.pedidos.outbox.entity.OutboxEvent;
import cl.duoc.pedidos360.pedidos.outbox.entity.OutboxStatus;
import cl.duoc.pedidos360.pedidos.outbox.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);

    private final OutboxEventRepository outboxEventRepository;
    private final MeterRegistry meterRegistry;

    @Value("${kafka.outbox.max-retries:5}")
    private int maxRetries;

    @Value("${kafka.outbox.initial-backoff-ms:1000}")
    private long initialBackoffMs;

    @Value("${kafka.outbox.max-backoff-ms:60000}")
    private long maxBackoffMs;

    public OutboxService(OutboxEventRepository outboxEventRepository) {
        this(outboxEventRepository, null);
    }

    @Autowired
    public OutboxService(OutboxEventRepository outboxEventRepository,
                         @Autowired(required = false) MeterRegistry meterRegistry) {
        this.outboxEventRepository = outboxEventRepository;
        this.meterRegistry = meterRegistry;
        registerMetrics();
    }

    private void registerMetrics() {
        if (meterRegistry != null) {
            Gauge.builder("pedidos360.outbox.pending", () -> outboxEventRepository.countByStatus(OutboxStatus.PENDING))
                    .description("Cantidad de eventos outbox pendientes")
                    .register(meterRegistry);

            Gauge.builder("pedidos360.outbox.failed", () -> outboxEventRepository.countByStatus(OutboxStatus.FAILED))
                    .description("Cantidad de eventos outbox fallidos")
                    .register(meterRegistry);
        }
    }

    /**
     * Guarda el evento en la tabla outbox dentro de la misma transacción de negocio del pedido.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public OutboxEvent saveEvent(String eventId, String aggregateType, String aggregateId,
                                 String eventType, String topic, String kafkaKey,
                                 String correlationId, String payload) {
        OutboxEvent event = new OutboxEvent(
                eventId,
                aggregateType,
                aggregateId,
                eventType,
                topic,
                kafkaKey,
                correlationId,
                payload
        );
        OutboxEvent saved = outboxEventRepository.save(event);
        log.info("[OUTBOX] Event registered atomically in outbox: id={} eventId={} type={} key={}",
                saved.getId(), saved.getEventId(), saved.getEventType(), saved.getKafkaKey());
        return saved;
    }

    /**
     * Reserva un lote de eventos listos para ser publicados usando FOR UPDATE SKIP LOCKED.
     * Cambia el estado a PROCESSING y libera el bloqueo de base de datos inmediatamente al commitear.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<OutboxEvent> reserveBatchForPublishing(String lockedBy, int batchSize, long stuckTimeoutSeconds) {
        Instant now = Instant.now();
        Instant stuckThreshold = now.minusSeconds(stuckTimeoutSeconds);

        List<OutboxEvent> candidates;
        try {
            candidates = outboxEventRepository.findAndLockBatchForProcessing(now, stuckThreshold, batchSize);
        } catch (Exception ex) {
            // Fallback para entornos donde la consulta nativa no sea compatible (ej. H2 en memoria para tests unitarios rápidos)
            log.warn("[OUTBOX] Native SKIP LOCKED query failed, falling back to findTop50: {}", ex.getMessage());
            candidates = outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
        }

        if (candidates.isEmpty()) {
            return List.of();
        }

        List<OutboxEvent> reserved = new ArrayList<>();
        for (OutboxEvent event : candidates) {
            event.setStatus(OutboxStatus.PROCESSING);
            event.setLockedBy(lockedBy);
            event.setProcessingStartedAt(now);
            reserved.add(outboxEventRepository.save(event));
        }

        log.debug("[OUTBOX] Batch reserved for publishing: count={} lockedBy={}", reserved.size(), lockedBy);
        return reserved;
    }

    /**
     * Marca el evento como publicado exitosamente tras recibir confirmación real (ACK) del broker Kafka.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsPublished(Long id) {
        outboxEventRepository.findById(id).ifPresent(event -> {
            event.setStatus(OutboxStatus.PUBLISHED);
            event.setPublishedAt(Instant.now());
            event.setLockedBy(null);
            event.setErrorMessage(null);
            event.setErrorType(null);
            outboxEventRepository.save(event);

            log.info("[OUTBOX] Event marked as PUBLISHED: id={} eventId={}", id, event.getEventId());

            if (meterRegistry != null) {
                meterRegistry.counter("pedidos360.outbox.published", "topic", event.getTopic()).increment();
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsFailed(Long id, String errorMessage) {
        markAsFailed(id, errorMessage, false);
    }

    /**
     * Registra un fallo de publicación con backoff exponencial. Si supera maxRetries o es permanente, pasa a FAILED.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsFailed(Long id, String errorMessage, boolean isPermanent) {
        outboxEventRepository.findById(id).ifPresent(event -> {
            int newRetry = event.getRetryCount() + 1;
            event.setRetryCount(newRetry);
            event.setErrorMessage(errorMessage);
            event.setErrorType(isPermanent ? "PERMANENT" : "TEMPORARY");
            event.setLastAttemptedAt(Instant.now());
            event.setLockedBy(null);

            if (isPermanent || newRetry >= maxRetries) {
                event.setStatus(OutboxStatus.FAILED);
                event.setNextRetryAt(null);
                log.error("[OUTBOX] Event moved to FAILED (permanent={}, attempts={}/{}): id={} eventId={} error={}",
                        isPermanent, newRetry, maxRetries, id, event.getEventId(), errorMessage);

                if (meterRegistry != null) {
                    meterRegistry.counter("pedidos360.outbox.failed", "topic", event.getTopic(), "type", event.getErrorType()).increment();
                }
            } else {
                long backoffMs = Math.min(initialBackoffMs * (1L << Math.min(newRetry, 10)), maxBackoffMs);
                event.setStatus(OutboxStatus.PENDING);
                event.setNextRetryAt(Instant.now().plusMillis(backoffMs));
                log.warn("[OUTBOX] Event publication attempt {} failed for id={}. Scheduled retry in {}ms: {}",
                        newRetry, id, backoffMs, errorMessage);
            }

            outboxEventRepository.save(event);
        });
    }

    /**
     * Permite la recuperación controlada de eventos en estado FAILED para su reintento seguro.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int reprocessFailedEvents(int limit) {
        List<OutboxEvent> failedEvents = outboxEventRepository.findByStatus(OutboxStatus.FAILED);
        int reprocessed = 0;

        for (OutboxEvent event : failedEvents) {
            if (reprocessed >= limit) {
                break;
            }
            event.setStatus(OutboxStatus.PENDING);
            event.setRetryCount(0);
            event.setNextRetryAt(null);
            event.setLockedBy(null);
            event.setErrorMessage(null);
            event.setErrorType(null);
            outboxEventRepository.save(event);
            reprocessed++;
            log.info("[OUTBOX-RECOVERY] Event reset to PENDING for reprocessing: id={} eventId={}",
                    event.getId(), event.getEventId());
        }

        log.info("[OUTBOX-RECOVERY] Reprocessed {} failed outbox event(s)", reprocessed);
        return reprocessed;
    }

    @Transactional(readOnly = true)
    public List<OutboxEvent> findPendingEvents() {
        return outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
    }
}
