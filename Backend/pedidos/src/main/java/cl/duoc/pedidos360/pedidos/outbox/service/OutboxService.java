package cl.duoc.pedidos360.pedidos.outbox.service;

import cl.duoc.pedidos360.pedidos.outbox.entity.OutboxEvent;
import cl.duoc.pedidos360.pedidos.outbox.entity.OutboxStatus;
import cl.duoc.pedidos360.pedidos.outbox.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);
    private static final int MAX_RETRIES = 5;

    private final OutboxEventRepository outboxEventRepository;

    public OutboxService(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    /**
     * Guarda el evento en la tabla outbox dentro de la misma transacción del pedido.
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
        log.info("[OUTBOX] Event registered in outbox: id={} eventId={} type={} key={}",
                saved.getId(), saved.getEventId(), saved.getEventType(), saved.getKafkaKey());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<OutboxEvent> findPendingEvents() {
        return outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsPublished(Long id) {
        outboxEventRepository.findById(id).ifPresent(event -> {
            event.setStatus(OutboxStatus.PUBLISHED);
            event.setPublishedAt(Instant.now());
            event.setErrorMessage(null);
            outboxEventRepository.save(event);
            log.info("[OUTBOX] Event marked as PUBLISHED: id={} eventId={}", id, event.getEventId());
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsFailed(Long id, String errorMessage) {
        outboxEventRepository.findById(id).ifPresent(event -> {
            int newRetry = event.getRetryCount() + 1;
            event.setRetryCount(newRetry);
            event.setErrorMessage(errorMessage);
            if (newRetry >= MAX_RETRIES) {
                event.setStatus(OutboxStatus.FAILED);
                log.error("[OUTBOX] Event marked as FAILED after {} retries: id={} error={}",
                        newRetry, id, errorMessage);
            } else {
                event.setStatus(OutboxStatus.PENDING);
                log.warn("[OUTBOX] Event publication attempt {} failed for id={}: {}",
                        newRetry, id, errorMessage);
            }
            outboxEventRepository.save(event);
        });
    }
}

