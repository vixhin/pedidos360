package cl.duoc.pedidos360.pedidos.kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = false)
public class KafkaTopicsConfig {

    @Value("${kafka.topics.pedidos:pedidos360.pedidos.v1}")
    private String pedidosTopic;

    @Value("${kafka.topics.pedidos-dlt:pedidos360.pedidos.dlt.v1}")
    private String pedidosDltTopic;

    @Value("${kafka.topics.partitions:3}")
    private int partitions;

    @Value("${kafka.topics.replication-factor:1}")
    private int replicationFactor;

    @Bean
    public NewTopic pedidosTopic() {
        return TopicBuilder.name(pedidosTopic)
                .partitions(partitions)
                .replicas(replicationFactor)
                .config("retention.ms", "86400000") // 24 horas
                .build();
    }

    @Bean
    public NewTopic pedidosDltTopic() {
        return TopicBuilder.name(pedidosDltTopic)
                .partitions(partitions)
                .replicas(replicationFactor)
                .config("retention.ms", "604800000") // 7 días
                .build();
    }

    public String getPedidosTopic() {
        return pedidosTopic;
    }

    public String getPedidosDltTopic() {
        return pedidosDltTopic;
    }
}

