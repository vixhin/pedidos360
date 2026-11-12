package cl.duoc.pedidos360.analitica.dto;

import java.util.HashMap;
import java.util.Map;

public class KafkaTopicResponseDTO {

    private String name;
    private int partitions;
    private int replicationFactor;
    private Map<String, String> configs = new HashMap<>();

    public KafkaTopicResponseDTO() {}

    public KafkaTopicResponseDTO(String name, int partitions, int replicationFactor, Map<String, String> configs) {
        this.name = name;
        this.partitions = partitions;
        this.replicationFactor = replicationFactor;
        this.configs = configs != null ? configs : new HashMap<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPartitions() {
        return partitions;
    }

    public void setPartitions(int partitions) {
        this.partitions = partitions;
    }

    public int getReplicationFactor() {
        return replicationFactor;
    }

    public void setReplicationFactor(int replicationFactor) {
        this.replicationFactor = replicationFactor;
    }

    public Map<String, String> getConfigs() {
        return configs;
    }

    public void setConfigs(Map<String, String> configs) {
        this.configs = configs;
    }
}

