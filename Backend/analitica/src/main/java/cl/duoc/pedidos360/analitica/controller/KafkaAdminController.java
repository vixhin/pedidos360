package cl.duoc.pedidos360.analitica.controller;

import cl.duoc.pedidos360.analitica.dto.*;
import cl.duoc.pedidos360.analitica.service.KafkaAdminService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/admin/kafka")
public class KafkaAdminController {

    private final KafkaAdminService kafkaAdminService;

    public KafkaAdminController(KafkaAdminService kafkaAdminService) {
        this.kafkaAdminService = kafkaAdminService;
    }

    // =========================================================================
    // TOPICS
    // =========================================================================

    @GetMapping("/topics")
    public ResponseEntity<ApiResponse<List<KafkaTopicResponseDTO>>> listTopics() {
        try {
            List<KafkaTopicResponseDTO> topics = kafkaAdminService.listTopics();
            return ResponseEntity.ok(ApiResponse.ok("Tópicos obtenidos exitosamente", topics));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/topics/{name}")
    public ResponseEntity<ApiResponse<KafkaTopicResponseDTO>> describeTopic(@PathVariable String name) {
        try {
            KafkaTopicResponseDTO topic = kafkaAdminService.describeTopic(name);
            return ResponseEntity.ok(ApiResponse.ok("Tópico obtenido exitosamente", topic));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PostMapping("/topics")
    public ResponseEntity<ApiResponse<KafkaTopicResponseDTO>> createTopic(@RequestBody KafkaTopicCreateDTO dto) {
        try {
            KafkaTopicResponseDTO created = kafkaAdminService.createTopic(dto);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.ok("Tópico creado exitosamente", created));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/topics/{name}")
    public ResponseEntity<ApiResponse<Void>> updateTopic(@PathVariable String name,
                                                         @RequestBody KafkaTopicUpdateDTO dto) {
        try {
            kafkaAdminService.updateTopic(name, dto);
            return ResponseEntity.ok(ApiResponse.ok("Tópico actualizado exitosamente", null));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @DeleteMapping("/topics/{name}")
    public ResponseEntity<ApiResponse<Void>> deleteTopic(@PathVariable String name) {
        try {
            kafkaAdminService.deleteTopic(name);
            return ResponseEntity.ok(ApiResponse.ok("Tópico eliminado exitosamente", null));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // =========================================================================
    // CONSUMER GROUPS
    // =========================================================================

    @GetMapping("/consumer-groups")
    public ResponseEntity<ApiResponse<List<KafkaConsumerGroupDTO>>> listConsumerGroups() {
        try {
            List<KafkaConsumerGroupDTO> groups = kafkaAdminService.listConsumerGroups();
            return ResponseEntity.ok(ApiResponse.ok("Grupos de consumidores obtenidos exitosamente", groups));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/consumer-groups/{groupId}")
    public ResponseEntity<ApiResponse<KafkaConsumerGroupDTO>> describeConsumerGroup(@PathVariable String groupId) {
        try {
            KafkaConsumerGroupDTO group = kafkaAdminService.describeConsumerGroup(groupId);
            return ResponseEntity.ok(ApiResponse.ok("Grupo de consumidores obtenido exitosamente", group));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
}

