package cl.duoc.pedidos360.analitica.kafka.consumer;

import cl.duoc.pedidos360.analitica.entity.AnaliticaEvento;
import cl.duoc.pedidos360.analitica.service.AnaliticaService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = false)
public class AnaliticaDltConsumer {

    private static final Logger log = LoggerFactory.getLogger(AnaliticaDltConsumer.class);

    private final AnaliticaService analiticaService;

    public AnaliticaDltConsumer(AnaliticaService analiticaService) {
        this.analiticaService = analiticaService;
    }

    @KafkaListener(
            topics = "${kafka.topics.pedidos-dlt:pedidos360.pedidos.dlt.v1}",
            groupId = "${spring.kafka.consumer.dlt-group-id:analitica-dlt-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeDlt(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        log.error("[ANALITICA-DLT] Dead letter message received: topic={} partition={} offset={} key={} value={}",
                record.topic(), record.partition(), record.offset(), record.key(), record.value());

        try {
            String descripcion = String.format("Mensaje derivado a DLT: topic=%s partition=%d offset=%d key=%s",
                    record.topic(), record.partition(), record.offset(), record.key());

            analiticaService.registrarEvento(new AnaliticaEvento(null, "ERROR_DLT", descripcion));

        } catch (Exception ex) {
            log.error("[ANALITICA-DLT] Error logging DLT metric: {}", ex.getMessage());
        } finally {
            acknowledgment.acknowledge();
        }
    }
}

