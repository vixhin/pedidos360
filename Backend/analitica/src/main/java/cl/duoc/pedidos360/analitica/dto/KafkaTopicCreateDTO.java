package cl.duoc.pedidos360.analitica.dto;

import java.util.HashMap;
import java.util.Map;

public class KafkaTopicCreateDTO {

    private String name;
    private int partitions = 3;
    private short replicationFactor = 1;
    private Long retentionMs = 86400000L; // 24 horas por defecto
    private String cleanupPolicy = "delete";
    private Map<String, String> configs = new HashMap<>();

    public KafkaTopicCreateDTO() {}

    public KafkaTopicCreateDTO(String name, int partitions, short replicationFactor) {
        this.name = name;
        this.partitions = partitions;
        this.replicationFactor = replicationFactor;
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

    public short getReplicationFactor() {
        return replicationFactor;
    }

    public void setReplicationFactor(short replicationFactor) {
        this.replicationFactor = replicationFactor;
    }

    public Long getRetentionMs() {
        return retentionMs;
    }

    public void setRetentionMs(Long retentionMs) {
        this.retentionMs = retentionMs;
    }

    public String getCleanupPolicy() {
        return cleanupPolicy;
    }

    public void setCleanupPolicy(String cleanupPolicy) {
        this.cleanupPolicy = cleanupPolicy;
    }

    public Map<String, String> getConfigs() {
        return configs;
    }

    public void setConfigs(Map<String, String> configs) {
        this.configs = configs;
    }
}

