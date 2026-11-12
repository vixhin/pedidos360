package cl.duoc.pedidos360.analitica.dto;

import java.util.HashMap;
import java.util.Map;

public class KafkaTopicUpdateDTO {

    private Integer totalPartitions;
    private Map<String, String> configs = new HashMap<>();

    public KafkaTopicUpdateDTO() {}

    public Integer getTotalPartitions() {
        return totalPartitions;
    }

    public void setTotalPartitions(Integer totalPartitions) {
        this.totalPartitions = totalPartitions;
    }

    public Map<String, String> getConfigs() {
        return configs;
    }

    public void setConfigs(Map<String, String> configs) {
        this.configs = configs;
    }
}

