package cl.duoc.pedidos360.analitica.service;

import cl.duoc.pedidos360.analitica.entity.ProcessedEvent;
import cl.duoc.pedidos360.analitica.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final ProcessedEventRepository processedEventRepository;

    public IdempotencyService(ProcessedEventRepository processedEventRepository) {
        this.processedEventRepository = processedEventRepository;
    }

    @Transactional(readOnly = true)
    public boolean isAlreadyProcessed(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return false;
        }
        return processedEventRepository.existsByEventId(eventId);
    }

    @Transactional(readOnly = true)
    public boolean isResourceEventAlreadyProcessed(String resourceId, String eventType) {
        if (resourceId == null || eventType == null) {
            return false;
        }
        return processedEventRepository.existsByResourceIdAndEventType(resourceId, eventType);
    }

    @Transactional
    public void markAsProcessed(String eventId, String sourceChannel, String eventType,
                                String resourceId, String correlationId, String consumerGroup) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }
        try {
            ProcessedEvent event = new ProcessedEvent(
                    eventId,
                    sourceChannel,
                    eventType,
                    resourceId,
                    correlationId,
                    consumerGroup,
                    Instant.now()
            );
            processedEventRepository.save(event);
            log.info("[IDEMPOTENCY] Event marked as processed: eventId={} channel={} type={} resourceId={}",
                    eventId, sourceChannel, eventType, resourceId);
        } catch (Exception ex) {
            log.warn("[IDEMPOTENCY] Could not mark event as processed (possibly concurrent duplicate): eventId={}, error={}",
                    eventId, ex.getMessage());
        }
    }
}

