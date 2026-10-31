package cl.duoc.pedidos360.analitica.service;

import cl.duoc.pedidos360.analitica.entity.AnaliticaEvento;
import cl.duoc.pedidos360.analitica.entity.ProcessedEvent;
import cl.duoc.pedidos360.analitica.repository.AnaliticaRepository;
import cl.duoc.pedidos360.analitica.repository.ProcessedEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final ProcessedEventRepository processedEventRepository;
    private final AnaliticaRepository analiticaRepository;
    private final MeterRegistry meterRegistry;

    public IdempotencyService(ProcessedEventRepository processedEventRepository,
                              AnaliticaRepository analiticaRepository) {
        this(processedEventRepository, analiticaRepository, null);
    }

    @Autowired
    public IdempotencyService(ProcessedEventRepository processedEventRepository,
                              AnaliticaRepository analiticaRepository,
                              @Autowired(required = false) MeterRegistry meterRegistry) {
        this.processedEventRepository = processedEventRepository;
        this.analiticaRepository = analiticaRepository;
        this.meterRegistry = meterRegistry;
    }

    @Transactional(readOnly = true)
    public boolean isAlreadyProcessed(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return false;
        }
        return processedEventRepository.existsByEventId(eventId);
    }

    /**
     * Procesa un evento y su métrica analítica dentro de una misma transacción PostgreSQL,
     * garantizando atomicidad e idempotencia estricta mediante restricción UNIQUE.
     *
     * @param event Información del evento recibido (Kafka o RabbitMQ).
     * @param metrica Métrica analítica a registrar si el evento es nuevo.
     * @return true si el evento fue procesado y la métrica registrada; false si es un duplicado.
     * @throws RuntimeException ante cualquier fallo en la base de datos (para permitir reintentos/DLT/DLQ).
     */
    @Transactional
    public boolean processEventTransactional(ProcessedEvent event, AnaliticaEvento metrica) {
        if (event == null || event.getEventId() == null || event.getEventId().isBlank()) {
            throw new IllegalArgumentException("El eventId no puede ser nulo o vacío para procesamiento idempotente");
        }

        long startTime = System.currentTimeMillis();

        // 1. Intentar registrar el evento usando restricción UNIQUE atómica en PostgreSQL
        int rowsInserted = processedEventRepository.insertIfNotExists(
                event.getEventId(),
                event.getSourceChannel(),
                event.getEventType(),
                event.getResourceId(),
                event.getCorrelationId(),
                event.getConsumerGroup(),
                event.getProcessedAt() != null ? event.getProcessedAt() : Instant.now(),
                event.getStatus() != null ? event.getStatus() : "COMPLETED",
                event.getErrorMessage(),
                event.getExecutionDurationMs()
        );

        if (rowsInserted == 0) {
            // El evento ya existía (duplicado detectado de forma segura ante concurrencia)
            log.info("[IDEMPOTENCY] Duplicate event detected via UNIQUE constraint (skipping metric): eventId={} channel={} type={}",
                    event.getEventId(), event.getSourceChannel(), event.getEventType());

            if (meterRegistry != null) {
                meterRegistry.counter("pedidos360.events.duplicate",
                        "channel", event.getSourceChannel() != null ? event.getSourceChannel() : "UNKNOWN",
                        "type", event.getEventType() != null ? event.getEventType() : "UNKNOWN"
                ).increment();
            }
            return false;
        }

        // 2. Registro atómico de la métrica analítica en la misma transacción
        if (metrica != null) {
            analiticaRepository.save(metrica);
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("[IDEMPOTENCY] Event processed and metric recorded atomically: eventId={} channel={} type={} durationMs={}",
                event.getEventId(), event.getSourceChannel(), event.getEventType(), duration);

        if (meterRegistry != null) {
            meterRegistry.counter("pedidos360.events.processed",
                    "channel", event.getSourceChannel() != null ? event.getSourceChannel() : "UNKNOWN",
                    "type", event.getEventType() != null ? event.getEventType() : "UNKNOWN"
            ).increment();
        }

        return true;
    }
}
