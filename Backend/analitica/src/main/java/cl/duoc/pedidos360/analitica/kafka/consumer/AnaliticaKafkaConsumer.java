package cl.duoc.pedidos360.analitica.kafka.consumer;

import cl.duoc.pedidos360.analitica.entity.AnaliticaEvento;
import cl.duoc.pedidos360.analitica.entity.ProcessedEvent;
import cl.duoc.pedidos360.analitica.kafka.event.KafkaEvent;
import cl.duoc.pedidos360.analitica.service.IdempotencyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = false)
public class AnaliticaKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(AnaliticaKafkaConsumer.class);

    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    public AnaliticaKafkaConsumer(IdempotencyService idempotencyService,
                                  ObjectMapper objectMapper) {
        this(idempotencyService, objectMapper, null);
    }

    @Autowired
    public AnaliticaKafkaConsumer(IdempotencyService idempotencyService,
                                  ObjectMapper objectMapper,
                                  @Autowired(required = false) MeterRegistry meterRegistry) {
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(
            topics = "${kafka.topics.pedidos:pedidos360.pedidos.v1}",
            groupId = "${spring.kafka.consumer.group-id:analitica-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        String key = record.key();
        String rawValue = record.value();

        log.info("[ANALITICA-KAFKA] Record received from topic={} partition={} offset={} key={}",
                record.topic(), record.partition(), record.offset(), key);

        if (rawValue == null || rawValue.isBlank()) {
            log.error("[ANALITICA-KAFKA] Malformed record: payload is null or empty. Routing to DLT via exception.");
            throw new IllegalArgumentException("Payload de evento Kafka vacío o nulo en topic=" + record.topic());
        }

        try {
            // 1. Deserialización y validación estricta del contrato de evento
            KafkaEvent event;
            try {
                event = objectMapper.readValue(rawValue, KafkaEvent.class);
            } catch (Exception ex) {
                log.error("[ANALITICA-KAFKA] Deserialization error for record key={} from topic={}: {}",
                        key, record.topic(), ex.getMessage());
                throw new IllegalArgumentException("Error de deserialización de evento Kafka: " + ex.getMessage(), ex);
            }

            if (event == null || event.getEventId() == null || event.getEventId().isBlank()) {
                log.error("[ANALITICA-KAFKA] Invalid contract: event or eventId missing in payload={}", rawValue);
                throw new IllegalArgumentException("Contrato de evento inválido: falta eventId");
            }

            // 2. Construcción de métrica según tipo de evento
            Map<String, Object> payload = event.getPayload();
            String eventType = event.getEventType() != null ? event.getEventType() : "DESCONOCIDO";
            Long resourceId = event.getResourceId();

            String tipoMetrica;
            String descripcion;

            switch (eventType) {
                case "pedido.creado" -> {
                    tipoMetrica = "PEDIDO_CREADO";
                    Object usuarioId = payload != null ? payload.get("usuarioId") : "N/A";
                    Object total = payload != null ? payload.get("total") : "0";
                    descripcion = String.format("Pedido #%d creado por usuarioId=%s por un total de $%s (via Kafka)",
                            resourceId, usuarioId, total);
                }
                case "pedido.estado.actualizado" -> {
                    tipoMetrica = "PEDIDO_ESTADO_ACTUALIZADO";
                    Object nuevoEstado = payload != null ? payload.get("nuevoEstado") : "DESCONOCIDO";
                    descripcion = String.format("Pedido #%d cambio a estado: %s (via Kafka)",
                            resourceId, nuevoEstado);
                }
                case "pedido.repartidor.asignado" -> {
                    tipoMetrica = "REPARTIDOR_ASIGNADO";
                    Object repartidorId = payload != null ? payload.get("repartidorId") : "N/A";
                    Object nombreRepartidor = payload != null ? payload.get("nombreRepartidor") : "N/A";
                    descripcion = String.format("Repartidor %s (ID=%s) asignado al pedido #%d (via Kafka)",
                            nombreRepartidor, repartidorId, resourceId);
                }
                default -> {
                    tipoMetrica = "EVENTO_KAFKA_GENERICO";
                    descripcion = String.format("Evento %s procesado para recurso #%d (via Kafka)",
                            eventType, resourceId);
                }
            }

            AnaliticaEvento metrica = new AnaliticaEvento(null, tipoMetrica, descripcion);

            // 3. Procesamiento transaccional atómico e idempotente
            ProcessedEvent processedEvent = new ProcessedEvent(
                    event.getEventId(),
                    "KAFKA",
                    eventType,
                    resourceId != null ? resourceId.toString() : null,
                    event.getCorrelationId(),
                    "analitica-group",
                    Instant.now()
            );

            boolean processed = idempotencyService.processEventTransactional(processedEvent, metrica);

            // 4. Confirmación ACK únicamente tras éxito transaccional
            acknowledgment.acknowledge();

            if (processed) {
                log.info("[ANALITICA-KAFKA] Event processed and ACKed successfully: eventId={} type={}",
                        event.getEventId(), eventType);
            } else {
                log.info("[ANALITICA-KAFKA] Duplicate event ACKed without metric duplication: eventId={}",
                        event.getEventId());
            }

        } catch (Exception ex) {
            log.error("[ANALITICA-KAFKA] Error processing record key={} from topic={}: {}",
                    key, record.topic(), ex.getMessage(), ex);

            if (meterRegistry != null) {
                meterRegistry.counter("pedidos360.consumer.errors", "channel", "kafka").increment();
            }

            // Propagar para activar DefaultErrorHandler (reintentos con backoff y routing a DLT)
            throw new RuntimeException("Fallo en consumo de evento Kafka: " + ex.getMessage(), ex);
        }
    }
}
