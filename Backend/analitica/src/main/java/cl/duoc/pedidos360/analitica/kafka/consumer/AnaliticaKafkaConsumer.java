package cl.duoc.pedidos360.analitica.kafka.consumer;

import cl.duoc.pedidos360.analitica.entity.AnaliticaEvento;
import cl.duoc.pedidos360.analitica.kafka.event.KafkaEvent;
import cl.duoc.pedidos360.analitica.service.AnaliticaService;
import cl.duoc.pedidos360.analitica.service.IdempotencyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = false)
public class AnaliticaKafkaConsumer {

    private static final Logger log = LoggerFactory.getLogger(AnaliticaKafkaConsumer.class);

    private final AnaliticaService analiticaService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public AnaliticaKafkaConsumer(AnaliticaService analiticaService,
                                  IdempotencyService idempotencyService,
                                  ObjectMapper objectMapper) {
        this.analiticaService = analiticaService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${kafka.topics.pedidos:pedidos360.pedidos.v1}",
            groupId = "${spring.kafka.consumer.group-id:analitica-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        String key = record.key();
        String jsonValue = record.value();

        log.info("[ANALITICA-KAFKA] Received message: topic={} partition={} offset={} key={}",
                record.topic(), record.partition(), record.offset(), key);

        try {
            KafkaEvent event = objectMapper.readValue(jsonValue, KafkaEvent.class);

            // 1. Validación de mensaje
            if (event == null || event.getEventId() == null || event.getEventType() == null) {
                log.error("[ANALITICA-KAFKA] Invalid message structure. Skipping invalid payload: key={}", key);
                acknowledgment.acknowledge();
                return;
            }

            // 2. Idempotencia persistente en Analítica
            if (idempotencyService.isAlreadyProcessed(event.getEventId())) {
                log.info("[ANALITICA-KAFKA] Duplicate event ignored (already processed): eventId={} key={}",
                        event.getEventId(), key);
                acknowledgment.acknowledge();
                return;
            }

            // 3. Procesamiento según tipo de evento
            Map<String, Object> payload = event.getPayload();
            String eventType = event.getEventType();
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

            analiticaService.registrarEvento(new AnaliticaEvento(null, tipoMetrica, descripcion));

            // 4. Registrar idempotencia en base de datos PostgreSQL
            idempotencyService.markAsProcessed(
                    event.getEventId(),
                    "KAFKA",
                    event.getEventType(),
                    resourceId != null ? resourceId.toString() : null,
                    event.getCorrelationId(),
                    "analitica-group"
            );

            acknowledgment.acknowledge();
            log.info("[ANALITICA-KAFKA] Event processed and ACKed successfully: eventId={} type={}",
                    event.getEventId(), event.getEventType());

        } catch (Exception ex) {
            log.error("[ANALITICA-KAFKA] Error processing record key={} from topic={}: {}",
                    key, record.topic(), ex.getMessage(), ex);
            // Lanzar excepción para que actúe el DefaultErrorHandler (reintentos y envío a DLT)
            throw new RuntimeException("Fallo en consumo de evento Kafka: " + ex.getMessage(), ex);
        }
    }
}

