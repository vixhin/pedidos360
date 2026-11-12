package cl.duoc.pedidos360.analitica.controller;

import cl.duoc.pedidos360.analitica.dto.*;
import cl.duoc.pedidos360.analitica.service.KafkaAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaAdminControllerTest {

    @Mock
    private KafkaAdminService kafkaAdminService;

    @InjectMocks
    private KafkaAdminController controller;

    private KafkaTopicResponseDTO mockTopic;
    private KafkaConsumerGroupDTO mockGroup;

    @BeforeEach
    void setUp() {
        mockTopic = new KafkaTopicResponseDTO("pedidos360.pedidos.v1", 3, 1, Map.of("retention.ms", "86400000"));
        mockGroup = new KafkaConsumerGroupDTO("analitica-group", "STABLE", 2, "localhost:9092");
    }

    @Test
    @DisplayName("GET /api/admin/kafka/topics -> 200 OK con lista de tópicos")
    void testListTopicsExito() {
        when(kafkaAdminService.listTopics()).thenReturn(List.of(mockTopic));

        ResponseEntity<ApiResponse<List<KafkaTopicResponseDTO>>> response = controller.listTopics();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).hasSize(1);
        assertThat(response.getBody().getData().get(0).getName()).isEqualTo("pedidos360.pedidos.v1");
    }

    @Test
    @DisplayName("GET /api/admin/kafka/topics/{name} existente -> 200 OK")
    void testDescribeTopicExito() {
        when(kafkaAdminService.describeTopic("pedidos360.pedidos.v1")).thenReturn(mockTopic);

        ResponseEntity<ApiResponse<KafkaTopicResponseDTO>> response = controller.describeTopic("pedidos360.pedidos.v1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getName()).isEqualTo("pedidos360.pedidos.v1");
    }

    @Test
    @DisplayName("GET /api/admin/kafka/topics/{name} inexistente -> 404 NOT_FOUND")
    void testDescribeTopicInexistente() {
        when(kafkaAdminService.describeTopic("inexistente")).thenThrow(new NoSuchElementException("No existe"));

        ResponseEntity<ApiResponse<KafkaTopicResponseDTO>> response = controller.describeTopic("inexistente");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    @Test
    @DisplayName("POST /api/admin/kafka/topics -> 201 CREATED")
    void testCreateTopicExito() {
        KafkaTopicCreateDTO createDTO = new KafkaTopicCreateDTO("nuevo-topico", 3, (short) 1);
        when(kafkaAdminService.createTopic(any(KafkaTopicCreateDTO.class))).thenReturn(mockTopic);

        ResponseEntity<ApiResponse<KafkaTopicResponseDTO>> response = controller.createTopic(createDTO);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
    }

    @Test
    @DisplayName("PUT /api/admin/kafka/topics/{name} -> 200 OK")
    void testUpdateTopicExito() {
        KafkaTopicUpdateDTO updateDTO = new KafkaTopicUpdateDTO();
        updateDTO.setTotalPartitions(5);
        doNothing().when(kafkaAdminService).updateTopic(eq("pedidos360.pedidos.v1"), any(KafkaTopicUpdateDTO.class));

        ResponseEntity<ApiResponse<Void>> response = controller.updateTopic("pedidos360.pedidos.v1", updateDTO);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
    }

    @Test
    @DisplayName("DELETE /api/admin/kafka/topics/{name} -> 200 OK")
    void testDeleteTopicExito() {
        doNothing().when(kafkaAdminService).deleteTopic("topico-a-eliminar");

        ResponseEntity<ApiResponse<Void>> response = controller.deleteTopic("topico-a-eliminar");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
    }

    @Test
    @DisplayName("GET /api/admin/kafka/consumer-groups -> 200 OK")
    void testListConsumerGroupsExito() {
        when(kafkaAdminService.listConsumerGroups()).thenReturn(List.of(mockGroup));

        ResponseEntity<ApiResponse<List<KafkaConsumerGroupDTO>>> response = controller.listConsumerGroups();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).hasSize(1);
        assertThat(response.getBody().getData().get(0).getGroupId()).isEqualTo("analitica-group");
    }

    @Test
    @DisplayName("GET /api/admin/kafka/consumer-groups/{groupId} -> 200 OK")
    void testDescribeConsumerGroupExito() {
        when(kafkaAdminService.describeConsumerGroup("analitica-group")).thenReturn(mockGroup);

        ResponseEntity<ApiResponse<KafkaConsumerGroupDTO>> response = controller.describeConsumerGroup("analitica-group");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getState()).isEqualTo("STABLE");
    }
}

