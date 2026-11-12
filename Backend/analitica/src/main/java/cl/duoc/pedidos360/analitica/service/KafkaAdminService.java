package cl.duoc.pedidos360.analitica.service;

import cl.duoc.pedidos360.analitica.dto.KafkaConsumerGroupDTO;
import cl.duoc.pedidos360.analitica.dto.KafkaTopicCreateDTO;
import cl.duoc.pedidos360.analitica.dto.KafkaTopicResponseDTO;
import cl.duoc.pedidos360.analitica.dto.KafkaTopicUpdateDTO;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class KafkaAdminService {

    private static final Logger log = LoggerFactory.getLogger(KafkaAdminService.class);

    private final AdminClient adminClient;
    private final boolean externalClient;

    public KafkaAdminService(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
            @Autowired(required = false) AdminClient adminClient) {
        if (adminClient != null) {
            this.adminClient = adminClient;
            this.externalClient = true;
        } else {
            Map<String, Object> props = new HashMap<>();
            props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
            props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 10000);
            this.adminClient = AdminClient.create(props);
            this.externalClient = false;
        }
    }

    @PreDestroy
    public void close() {
        if (!externalClient && adminClient != null) {
            try {
                adminClient.close(Duration.ofSeconds(3));
            } catch (Exception e) {
                log.warn("[KAFKA-ADMIN] Error al cerrar AdminClient: {}", e.getMessage());
            }
        }
    }

    public List<KafkaTopicResponseDTO> listTopics() {
        try {
            Set<String> topicNames = adminClient.listTopics().names().get(10, TimeUnit.SECONDS);
            if (topicNames.isEmpty()) {
                return Collections.emptyList();
            }

            Map<String, TopicDescription> descriptions = adminClient.describeTopics(topicNames).allTopicNames().get(10, TimeUnit.SECONDS);

            List<KafkaTopicResponseDTO> result = new ArrayList<>();
            for (Map.Entry<String, TopicDescription> entry : descriptions.entrySet()) {
                String name = entry.getKey();
                TopicDescription desc = entry.getValue();
                int partitions = desc.partitions().size();
                int repFactor = desc.partitions().isEmpty() ? 0 : desc.partitions().get(0).replicas().size();

                Map<String, String> configs = getTopicConfigs(name);
                result.add(new KafkaTopicResponseDTO(name, partitions, repFactor, configs));
            }
            return result;
        } catch (Exception e) {
            log.error("[KAFKA-ADMIN] Error al listar tópicos: {}", e.getMessage());
            throw new RuntimeException("Error al listar tópicos de Kafka: " + extractMessage(e), e);
        }
    }

    public KafkaTopicResponseDTO describeTopic(String topicName) {
        try {
            TopicDescription desc = adminClient.describeTopics(Collections.singleton(topicName))
                    .allTopicNames().get(10, TimeUnit.SECONDS).get(topicName);

            int partitions = desc.partitions().size();
            int repFactor = desc.partitions().isEmpty() ? 0 : desc.partitions().get(0).replicas().size();
            Map<String, String> configs = getTopicConfigs(topicName);

            return new KafkaTopicResponseDTO(topicName, partitions, repFactor, configs);
        } catch (Exception e) {
            if (e.getCause() instanceof UnknownTopicOrPartitionException) {
                throw new NoSuchElementException("El tópico '" + topicName + "' no existe en Kafka.");
            }
            log.error("[KAFKA-ADMIN] Error al describir tópico {}: {}", topicName, e.getMessage());
            throw new RuntimeException("Error al describir tópico " + topicName + ": " + extractMessage(e), e);
        }
    }

    public KafkaTopicResponseDTO createTopic(KafkaTopicCreateDTO dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new IllegalArgumentException("El nombre del tópico es obligatorio.");
        }
        try {
            NewTopic newTopic = new NewTopic(dto.getName().trim(), dto.getPartitions(), dto.getReplicationFactor());
            Map<String, String> configs = new HashMap<>();

            if (dto.getRetentionMs() != null) {
                configs.put(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(dto.getRetentionMs()));
            }
            if (dto.getCleanupPolicy() != null) {
                configs.put(TopicConfig.CLEANUP_POLICY_CONFIG, dto.getCleanupPolicy());
            }
            if (dto.getConfigs() != null) {
                configs.putAll(dto.getConfigs());
            }
            newTopic.configs(configs);

            adminClient.createTopics(Collections.singleton(newTopic)).all().get(10, TimeUnit.SECONDS);
            log.info("[KAFKA-ADMIN] Tópico creado exitosamente: {}", dto.getName());

            return new KafkaTopicResponseDTO(dto.getName(), dto.getPartitions(), dto.getReplicationFactor(), configs);
        } catch (Exception e) {
            log.error("[KAFKA-ADMIN] Error al crear tópico {}: {}", dto.getName(), e.getMessage());
            throw new RuntimeException("Error al crear tópico " + dto.getName() + ": " + extractMessage(e), e);
        }
    }

    public void updateTopic(String topicName, KafkaTopicUpdateDTO dto) {
        try {
            if (dto.getTotalPartitions() != null) {
                adminClient.createPartitions(Map.of(topicName, NewPartitions.increaseTo(dto.getTotalPartitions())))
                        .all().get(10, TimeUnit.SECONDS);
                log.info("[KAFKA-ADMIN] Particiones aumentadas a {} para el tópico {}", dto.getTotalPartitions(), topicName);
            }

            if (dto.getConfigs() != null && !dto.getConfigs().isEmpty()) {
                ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, topicName);
                Collection<AlterConfigOp> alterOps = dto.getConfigs().entrySet().stream()
                        .map(entry -> new AlterConfigOp(
                                new org.apache.kafka.clients.admin.ConfigEntry(entry.getKey(), entry.getValue()),
                                AlterConfigOp.OpType.SET
                        ))
                        .collect(Collectors.toList());

                adminClient.incrementalAlterConfigs(Map.of(resource, alterOps)).all().get(10, TimeUnit.SECONDS);
                log.info("[KAFKA-ADMIN] Configuraciones actualizadas para el tópico {}: {}", topicName, dto.getConfigs());
            }
        } catch (Exception e) {
            log.error("[KAFKA-ADMIN] Error al actualizar tópico {}: {}", topicName, e.getMessage());
            throw new RuntimeException("Error al actualizar tópico " + topicName + ": " + extractMessage(e), e);
        }
    }

    public void deleteTopic(String topicName) {
        try {
            adminClient.deleteTopics(Collections.singleton(topicName)).all().get(10, TimeUnit.SECONDS);
            log.info("[KAFKA-ADMIN] Tópico eliminado exitosamente: {}", topicName);
        } catch (Exception e) {
            log.error("[KAFKA-ADMIN] Error al eliminar tópico {}: {}", topicName, e.getMessage());
            throw new RuntimeException("Error al eliminar tópico " + topicName + ": " + extractMessage(e), e);
        }
    }

    public List<KafkaConsumerGroupDTO> listConsumerGroups() {
        try {
            Collection<ConsumerGroupListing> listings = adminClient.listConsumerGroups().all().get(10, TimeUnit.SECONDS);
            List<String> groupIds = listings.stream().map(ConsumerGroupListing::groupId).collect(Collectors.toList());

            if (groupIds.isEmpty()) {
                return Collections.emptyList();
            }

            Map<String, ConsumerGroupDescription> descriptions = adminClient.describeConsumerGroups(groupIds)
                    .all().get(10, TimeUnit.SECONDS);

            List<KafkaConsumerGroupDTO> result = new ArrayList<>();
            for (Map.Entry<String, ConsumerGroupDescription> entry : descriptions.entrySet()) {
                String gId = entry.getKey();
                ConsumerGroupDescription desc = entry.getValue();
                String state = desc.state() != null ? desc.state().toString() : "UNKNOWN";
                int members = desc.members() != null ? desc.members().size() : 0;
                String coordinator = desc.coordinator() != null ? desc.coordinator().host() + ":" + desc.coordinator().port() : "N/A";
                result.add(new KafkaConsumerGroupDTO(gId, state, members, coordinator));
            }
            return result;
        } catch (Exception e) {
            log.error("[KAFKA-ADMIN] Error al listar grupos de consumidores: {}", e.getMessage());
            throw new RuntimeException("Error al listar grupos de consumidores: " + extractMessage(e), e);
        }
    }

    public KafkaConsumerGroupDTO describeConsumerGroup(String groupId) {
        try {
            ConsumerGroupDescription desc = adminClient.describeConsumerGroups(Collections.singleton(groupId))
                    .all().get(10, TimeUnit.SECONDS).get(groupId);

            if (desc == null) {
                throw new NoSuchElementException("El grupo de consumidores '" + groupId + "' no fue encontrado.");
            }

            String state = desc.state() != null ? desc.state().toString() : "UNKNOWN";
            int members = desc.members() != null ? desc.members().size() : 0;
            String coordinator = desc.coordinator() != null ? desc.coordinator().host() + ":" + desc.coordinator().port() : "N/A";
            return new KafkaConsumerGroupDTO(groupId, state, members, coordinator);
        } catch (Exception e) {
            log.error("[KAFKA-ADMIN] Error al describir grupo de consumidores {}: {}", groupId, e.getMessage());
            throw new RuntimeException("Error al describir grupo de consumidores " + groupId + ": " + extractMessage(e), e);
        }
    }

    private Map<String, String> getTopicConfigs(String topicName) {
        try {
            ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, topicName);
            org.apache.kafka.clients.admin.Config config = adminClient.describeConfigs(Collections.singleton(resource))
                    .all().get(5, TimeUnit.SECONDS).get(resource);

            Map<String, String> configs = new HashMap<>();
            if (config != null) {
                config.entries().forEach(entry -> {
                    if (!entry.isDefault()) {
                        configs.put(entry.name(), entry.value());
                    }
                });
            }
            return configs;
        } catch (Exception e) {
            log.debug("[KAFKA-ADMIN] No se pudieron obtener configuraciones para tópico {}: {}", topicName, e.getMessage());
            return Collections.emptyMap();
        }
    }

    private String extractMessage(Exception e) {
        return e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
    }
}
