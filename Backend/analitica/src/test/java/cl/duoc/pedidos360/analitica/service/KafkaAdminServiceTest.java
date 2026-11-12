package cl.duoc.pedidos360.analitica.service;

import cl.duoc.pedidos360.analitica.dto.KafkaTopicCreateDTO;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.CreateTopicsResult;
import org.apache.kafka.clients.admin.DeleteTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.KafkaFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaAdminServiceTest {

    @Mock
    private AdminClient adminClient;

    private KafkaAdminService kafkaAdminService;

    @BeforeEach
    void setUp() {
        kafkaAdminService = new KafkaAdminService("localhost:9092", adminClient);
    }

    @Test
    @DisplayName("createTopic con nombre nulo o vacío lanza IllegalArgumentException")
    void testCreateTopicNombreInvalido() {
        KafkaTopicCreateDTO dto = new KafkaTopicCreateDTO("", 3, (short) 1);

        assertThatThrownBy(() -> kafkaAdminService.createTopic(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("El nombre del tópico es obligatorio");
    }

    @Test
    @DisplayName("createTopic invoca adminClient.createTopics")
    void testCreateTopicExito() {
        KafkaTopicCreateDTO dto = new KafkaTopicCreateDTO("nuevo-topico", 3, (short) 1);
        dto.setRetentionMs(86400000L);
        dto.setCleanupPolicy("delete");

        CreateTopicsResult mockResult = mock(CreateTopicsResult.class);
        KafkaFuture<Void> future = KafkaFuture.completedFuture(null);
        when(mockResult.all()).thenReturn(future);
        when(adminClient.createTopics(any(Collection.class))).thenReturn(mockResult);

        var response = kafkaAdminService.createTopic(dto);

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("nuevo-topico");
        assertThat(response.getPartitions()).isEqualTo(3);
        verify(adminClient, times(1)).createTopics(any(Collection.class));
    }

    @Test
    @DisplayName("deleteTopic invoca adminClient.deleteTopics")
    void testDeleteTopicExito() {
        DeleteTopicsResult mockResult = mock(DeleteTopicsResult.class);
        KafkaFuture<Void> future = KafkaFuture.completedFuture(null);
        when(mockResult.all()).thenReturn(future);
        when(adminClient.deleteTopics(any(Collection.class))).thenReturn(mockResult);

        kafkaAdminService.deleteTopic("topico-antiguo");

        verify(adminClient, times(1)).deleteTopics(Collections.singleton("topico-antiguo"));
    }
}

