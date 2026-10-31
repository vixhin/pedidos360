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
import java.util.Optional;
import java.util.UUID;

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
     * Reserva un lote de eventos listos para ser publicados usando estrictamente FOR UPDATE SKIP LOCKED.
     * Asigna un identificador único de reserva (lease token) por operación.
     * La transacción se confirma inmediatamente para liberar el bloqueo de filas en PostgreSQL antes de llamar a Kafka.
     * Si la base de datos falla, la excepción se propaga sin ejecutar fallbacks inseguros.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<OutboxEvent> reserveBatchForPublishing(String lockedBy, int batchSize, long stuckTimeoutSeconds) {
        Instant now = Instant.now();
        Instant stuckThreshold = now.minusSeconds(stuckTimeoutSeconds);

        // Sin fallbacks inseguros: si la consulta SKIP LOCKED falla, se propaga el error
        List<OutboxEvent> candidates = outboxEventRepository.findAndLockBatchForProcessing(now, stuckThreshold, batchSize);

        if (candidates.isEmpty()) {
            return List.of();
        }

        List<OutboxEvent> reserved = new ArrayList<>();
        for (OutboxEvent event : candidates) {
            String reservationId = UUID.randomUUID().toString();
            event.setStatus(OutboxStatus.PROCESSING);
            event.setLockedBy(lockedBy);
            event.setReservationId(reservationId);
            event.setProcessingStartedAt(now);
            reserved.add(outboxEventRepository.save(event));
        }

        log.debug("[OUTBOX] Batch reserved securely: count={} lockedBy={}", reserved.size(), lockedBy);
        return reserved;
    }

    /**
     * Marca el evento como publicado validando atómicamente el identificador de reserva (lease token).
     * Si la reserva venció y fue tomada por otro publicador, la actualización condicional devuelve 0 filas
     * y se rechaza la sobrescritura.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markAsPublished(Long id, String reservationId) {
        int updated = outboxEventRepository.markPublishedConditional(id, reservationId, Instant.now());
        if (updated > 0) {
            log.info("[OUTBOX] Event marked as PUBLISHED: id={} reservationId={}", id, reservationId);
            if (meterRegistry != null) {
                meterRegistry.counter("pedidos360.outbox.published").increment();
            }
            return true;
        } else {
            log.warn("[OUTBOX] Rejected markAsPublished: event id={} lease expired or reclaimed (provided reservationId={})",
                    id, reservationId);
            return false;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsPublished(Long id) {
        outboxEventRepository.findById(id).ifPresent(event -> {
            event.setStatus(OutboxStatus.PUBLISHED);
            event.setPublishedAt(Instant.now());
            event.setLockedBy(null);
            event.setReservationId(null);
            event.setErrorMessage(null);
            event.setErrorType(null);
            outboxEventRepository.save(event);
            log.info("[OUTBOX] Event marked as PUBLISHED (legacy): id={}", id);
        });
    }

    /**
     * Registra un fallo de publicación validando condicionalmente el reservationId y estado PROCESSING.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markAsFailed(Long id, String reservationId, String errorMessage, boolean isPermanent) {
        Optional<OutboxEvent> opt = outboxEventRepository.findById(id);
        if (opt.isEmpty()) {
            log.warn("[OUTBOX] Cannot mark failed: event id={} not found", id);
            return false;
        }

        OutboxEvent event = opt.get();
        if (event.getStatus() != OutboxStatus.PROCESSING ||
                event.getReservationId() == null ||
                !event.getReservationId().equals(reservationId)) {
            log.warn("[OUTBOX] Rejected markAsFailed: event id={} reservationId mismatch or not in PROCESSING (currentRes={}, currentStatus={})",
                    id, event.getReservationId(), event.getStatus());
            return false;
        }

        int newRetry = event.getRetryCount() + 1;
        boolean maxReached = isPermanent || newRetry >= maxRetries;
        OutboxStatus newStatus = maxReached ? OutboxStatus.FAILED : OutboxStatus.PENDING;
        String errorType = isPermanent ? "PERMANENT" : "TEMPORARY";

        Instant nextRetryAt = null;
        if (!maxReached) {
            long backoffMs = Math.min(initialBackoffMs * (1L << Math.min(newRetry, 10)), maxBackoffMs);
            nextRetryAt = Instant.now().plusMillis(backoffMs);
            log.warn("[OUTBOX] Event attempt {} failed for id={}. Scheduled retry in {}ms: {}",
                    newRetry, id, backoffMs, errorMessage);
        } else {
            log.error("[OUTBOX] Event moved to FAILED (permanent={}, attempts={}/{}): id={} error={}",
                    isPermanent, newRetry, maxRetries, id, errorMessage);
            if (meterRegistry != null) {
                meterRegistry.counter("pedidos360.outbox.failed", "type", errorType).increment();
            }
        }

        int updated = outboxEventRepository.markFailedConditional(
                id,
                reservationId,
                newStatus,
                newRetry,
                nextRetryAt,
                errorMessage,
                errorType,
                Instant.now()
        );

        return updated > 0;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsFailed(Long id, String errorMessage) {
        markAsFailed(id, errorMessage, false);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsFailed(Long id, String errorMessage, boolean isPermanent) {
        outboxEventRepository.findById(id).ifPresent(event -> {
            markAsFailed(id, event.getReservationId() != null ? event.getReservationId() : "", errorMessage, isPermanent);
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
            event.setReservationId(null);
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
