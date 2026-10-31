package cl.duoc.pedidos360.analitica.kafka.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = false)
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:analitica-group}")
    private String defaultGroupId;

    @Value("${kafka.topics.pedidos-dlt:pedidos360.pedidos.dlt.v1}")
    private String dltTopic;

    @Value("${kafka.consumer.retry.interval-ms:1000}")
    private long retryIntervalMs;

    @Value("${kafka.consumer.retry.max-attempts:3}")
    private long maxAttempts;

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, defaultGroupId);

        // Deserializador seguro con manejo de errores (poison pills)
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, StringDeserializer.class);

        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 20);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ProducerFactory<String, String> dltProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> dltKafkaTemplate(ProducerFactory<String, String> dltProducerFactory) {
        return new KafkaTemplate<>(dltProducerFactory);
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> dltKafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                dltKafkaTemplate,
                (record, exception) -> {
                    // Evitar bucles infinitos: si el mensaje ya está en el DLT, no volver a enrutarlo al mismo DLT
                    if (record.topic().equals(dltTopic)) {
                        log.error("[KAFKA-ERROR-HANDLER] Prevented infinite loop: record is already in DLT topic={}", dltTopic);
                        return null;
                    }
                    log.error("[KAFKA-ERROR-HANDLER] Routing unrecoverable record key={} from topic={} partition={} offset={} to DLT={}. Reason: {}",
                            record.key(), record.topic(), record.partition(), record.offset(), dltTopic, exception.getMessage());
                    return new TopicPartition(dltTopic, record.partition());
                }
        );

        // Garantizar confirmación estricta de la publicación al DLT antes de dar por recuperado el mensaje
        recoverer.setFailIfSendResultIsError(true);

        // Reintentos controlados con backoff configurable
        FixedBackOff backOff = new FixedBackOff(retryIntervalMs, maxAttempts);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

        errorHandler.setRetryListeners((record, ex, attempt) -> {
            log.warn("[KAFKA-RETRY] Retrying message key={} from topic={} partition={} (attempt {}/{}): {}",
                    record.key(), record.topic(), record.partition(), attempt, maxAttempts, ex.getMessage());
        });

        return errorHandler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setConcurrency(2);
        return factory;
    }

    /**
     * Factory de listeners exclusivo para el Dead Letter Topic.
     * Implementa reintentos ante fallos de BD pero SIN DeadLetterPublishingRecoverer,
     * impidiendo terminantemente bucles infinitos de reenvío al mismo DLT.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> dltKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        DefaultErrorHandler dltErrorHandler = new DefaultErrorHandler(new FixedBackOff(retryIntervalMs, maxAttempts));
        dltErrorHandler.setRetryListeners((record, ex, attempt) -> {
            log.warn("[DLT-RETRY] Retrying DLT record key={} offset={} (attempt {}/{}): {}",
                    record.key(), record.offset(), attempt, maxAttempts, ex.getMessage());
        });

        factory.setCommonErrorHandler(dltErrorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setConcurrency(1);
        return factory;
    }
}
