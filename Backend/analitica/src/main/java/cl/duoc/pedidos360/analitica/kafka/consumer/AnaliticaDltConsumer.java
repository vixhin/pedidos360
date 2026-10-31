package cl.duoc.pedidos360.analitica.kafka.consumer;

import cl.duoc.pedidos360.analitica.entity.AnaliticaEvento;
import cl.duoc.pedidos360.analitica.entity.ProcessedEvent;
import cl.duoc.pedidos360.analitica.service.AnaliticaService;
import cl.duoc.pedidos360.analitica.service.IdempotencyService;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Component
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = false)
public class AnaliticaDltConsumer {

    private static final Logger log = LoggerFactory.getLogger(AnaliticaDltConsumer.class);

    private final AnaliticaService analiticaService;
    private final IdempotencyService idempotencyService;
    private final MeterRegistry meterRegistry;

    public AnaliticaDltConsumer(AnaliticaService analiticaService) {
        this(analiticaService, null, null);
    }

    public AnaliticaDltConsumer(AnaliticaService analiticaService,
                                @Autowired(required = false) IdempotencyService idempotencyService) {
        this(analiticaService, idempotencyService, null);
    }

    @Autowired
    public AnaliticaDltConsumer(AnaliticaService analiticaService,
                                @Autowired(required = false) IdempotencyService idempotencyService,
                                @Autowired(required = false) MeterRegistry meterRegistry) {
        this.analiticaService = analiticaService;
        this.idempotencyService = idempotencyService;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(
            topics = "${kafka.topics.pedidos-dlt:pedidos360.pedidos.dlt.v1}",
            groupId = "${spring.kafka.consumer.dlt-group-id:analitica-dlt-group}",
            containerFactory = "dltKafkaListenerContainerFactory"
    )
    public void consumeDlt(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        String originalTopic = extractHeader(record, KafkaHeaders.DLT_ORIGINAL_TOPIC, "N/A");
        String originalPartition = extractHeader(record, KafkaHeaders.DLT_ORIGINAL_PARTITION, "N/A");
        String originalOffset = extractHeader(record, KafkaHeaders.DLT_ORIGINAL_OFFSET, "N/A");
        String exceptionMessage = extractHeader(record, KafkaHeaders.DLT_EXCEPTION_MESSAGE, "Sin mensaje");

        log.error("[ANALITICA-DLT] Dead letter message received: dltTopic={} dltPartition={} dltOffset={} key={} | origTopic={} origPart={} origOffset={} error='{}'",
                record.topic(), record.partition(), record.offset(), record.key(),
                originalTopic, originalPartition, originalOffset, exceptionMessage);

        try {
            String descripcion = String.format("Mensaje derivado a DLT: topic=%s partition=%d offset=%d key=%s origTopic=%s origPartition=%s origOffset=%s error=%s",
                    record.topic(), record.partition(), record.offset(), record.key(), originalTopic, originalPartition, originalOffset, exceptionMessage);

            AnaliticaEvento metrica = new AnaliticaEvento(null, "ERROR_DLT", descripcion);

            // Identificador determinista para idempotencia en DLT basado en las coordenadas inmutables del mensaje
            String dltEventId = String.format("dlt-%s-%d-%d", record.topic(), record.partition(), record.offset());

            if (idempotencyService != null) {
                ProcessedEvent processedEvent = new ProcessedEvent(
                        dltEventId,
                        "DLT",
                        "ERROR_DLT",
                        record.key(),
                        originalTopic,
                        "analitica-dlt-group",
                        Instant.now()
                );
                // Persistencia atómica de la métrica y del evento en una única transacción PostgreSQL
                boolean processed = idempotencyService.processEventTransactional(processedEvent, metrica);
                if (!processed) {
                    log.info("[ANALITICA-DLT] Duplicate DLT message detected and skipped: dltEventId={}", dltEventId);
                } else {
                    log.info("[ANALITICA-DLT] DLT error event registered in PostgreSQL: dltEventId={}", dltEventId);
                }
            } else {
                analiticaService.registrarEvento(metrica);
            }

            if (meterRegistry != null) {
                meterRegistry.counter("pedidos360.dlt.messages", "origTopic", originalTopic).increment();
            }

            // Confirmación ACK únicamente tras éxito en la persistencia de base de datos
            acknowledgment.acknowledge();
            log.info("[ANALITICA-DLT] Message acknowledged successfully: offset={}", record.offset());

        } catch (Exception ex) {
            log.error("[ANALITICA-DLT] Persistence error processing DLT record offset={}: {}. Will NOT acknowledge (triggering retry).",
                    record.offset(), ex.getMessage(), ex);
            // No enviar ACK; propagar excepción para que el contenedor reintente con backoff
            throw new RuntimeException("Error en persistencia de mensaje DLT: " + ex.getMessage(), ex);
        }
    }

    private String extractHeader(ConsumerRecord<String, String> record, String headerName, String defaultValue) {
        Header header = record.headers().lastHeader(headerName);
        if (header != null && header.value() != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }
        return defaultValue;
    }
}
