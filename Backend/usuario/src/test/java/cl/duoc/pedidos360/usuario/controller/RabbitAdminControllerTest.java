package cl.duoc.pedidos360.usuario.controller;

import cl.duoc.pedidos360.usuario.dto.*;
import cl.duoc.pedidos360.usuario.service.RabbitAdminService;
import cl.duoc.pedidos360.usuario.service.RabbitTopologyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RabbitAdminControllerTest {

    @Mock
    private RabbitAdminService adminService;

    @Mock
    private RabbitTopologyService topologyService;

    @InjectMocks
    private RabbitAdminController controller;

    // --- QUEUES ---

    @Test
    @DisplayName("listQueues - returns 200 OK with queue list")
    void testListQueues() {
        RabbitQueueResponseDTO q = new RabbitQueueResponseDTO();
        q.setId(1L);
        q.setName("orders.queue");
        when(adminService.listQueues()).thenReturn(List.of(q));

        ResponseEntity<ApiResponse<List<RabbitQueueResponseDTO>>> response = controller.listQueues();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).hasSize(1);
        assertThat(response.getBody().getData().get(0).getName()).isEqualTo("orders.queue");
    }

    @Test
    @DisplayName("getQueueById - returns 200 OK with queue")
    void testGetQueueById() {
        RabbitQueueResponseDTO q = new RabbitQueueResponseDTO();
        q.setId(1L);
        q.setName("orders.queue");
        when(adminService.getQueueById(1L)).thenReturn(q);

        ResponseEntity<ApiResponse<RabbitQueueResponseDTO>> response = controller.getQueueById(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("createQueue - returns 201 CREATED")
    void testCreateQueue() {
        RabbitQueueDTO dto = new RabbitQueueDTO();
        dto.setName("new.queue");
        dto.setDurable(true);

        RabbitQueueResponseDTO created = new RabbitQueueResponseDTO();
        created.setId(2L);
        created.setName("new.queue");
        when(adminService.createQueue(any(RabbitQueueDTO.class))).thenReturn(created);

        ResponseEntity<ApiResponse<RabbitQueueResponseDTO>> response = controller.createQueue(dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getData().getName()).isEqualTo("new.queue");
    }

    @Test
    @DisplayName("updateQueue - returns 200 OK")
    void testUpdateQueue() {
        RabbitQueueDTO dto = new RabbitQueueDTO();
        dto.setName("updated.queue");

        RabbitQueueResponseDTO updated = new RabbitQueueResponseDTO();
        updated.setId(1L);
        updated.setName("updated.queue");
        when(adminService.updateQueue(eq(1L), any(RabbitQueueDTO.class))).thenReturn(updated);

        ResponseEntity<ApiResponse<RabbitQueueResponseDTO>> response = controller.updateQueue(1L, dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().getName()).isEqualTo("updated.queue");
    }

    @Test
    @DisplayName("deleteQueue - returns 200 OK")
    void testDeleteQueue() {
        doNothing().when(adminService).deleteQueue(1L);

        ResponseEntity<ApiResponse<Void>> response = controller.deleteQueue(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(adminService).deleteQueue(1L);
    }

    // --- EXCHANGES ---

    @Test
    @DisplayName("listExchanges - returns 200 OK")
    void testListExchanges() {
        RabbitExchangeResponseDTO e = new RabbitExchangeResponseDTO();
        e.setId(1L);
        e.setName("pedidos.events");
        when(adminService.listExchanges()).thenReturn(List.of(e));

        ResponseEntity<ApiResponse<List<RabbitExchangeResponseDTO>>> response = controller.listExchanges();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).hasSize(1);
    }

    @Test
    @DisplayName("getExchangeById - returns 200 OK")
    void testGetExchangeById() {
        RabbitExchangeResponseDTO e = new RabbitExchangeResponseDTO();
        e.setId(1L);
        e.setName("pedidos.events");
        when(adminService.getExchangeById(1L)).thenReturn(e);

        ResponseEntity<ApiResponse<RabbitExchangeResponseDTO>> response = controller.getExchangeById(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().getName()).isEqualTo("pedidos.events");
    }

    @Test
    @DisplayName("createExchange - returns 201 CREATED")
    void testCreateExchange() {
        RabbitExchangeDTO dto = new RabbitExchangeDTO();
        dto.setName("custom.topic");
        dto.setType("TOPIC");

        RabbitExchangeResponseDTO created = new RabbitExchangeResponseDTO();
        created.setId(2L);
        created.setName("custom.topic");
        when(adminService.createExchange(any(RabbitExchangeDTO.class))).thenReturn(created);

        ResponseEntity<ApiResponse<RabbitExchangeResponseDTO>> response = controller.createExchange(dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getData().getName()).isEqualTo("custom.topic");
    }

    @Test
    @DisplayName("updateExchange - returns 200 OK")
    void testUpdateExchange() {
        RabbitExchangeDTO dto = new RabbitExchangeDTO();
        dto.setName("custom.topic");

        RabbitExchangeResponseDTO updated = new RabbitExchangeResponseDTO();
        updated.setId(2L);
        updated.setName("custom.topic");
        when(adminService.updateExchange(eq(2L), any(RabbitExchangeDTO.class))).thenReturn(updated);

        ResponseEntity<ApiResponse<RabbitExchangeResponseDTO>> response = controller.updateExchange(2L, dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().getName()).isEqualTo("custom.topic");
    }

    @Test
    @DisplayName("deleteExchange - returns 200 OK")
    void testDeleteExchange() {
        doNothing().when(adminService).deleteExchange(2L);

        ResponseEntity<ApiResponse<Void>> response = controller.deleteExchange(2L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(adminService).deleteExchange(2L);
    }

    // --- BINDINGS ---

    @Test
    @DisplayName("listBindings - returns 200 OK")
    void testListBindings() {
        RabbitBindingResponseDTO b = new RabbitBindingResponseDTO();
        b.setId(1L);
        b.setRoutingKey("order.*");
        when(adminService.listBindings()).thenReturn(List.of(b));

        ResponseEntity<ApiResponse<List<RabbitBindingResponseDTO>>> response = controller.listBindings();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).hasSize(1);
    }

    @Test
    @DisplayName("getBindingById - returns 200 OK")
    void testGetBindingById() {
        RabbitBindingResponseDTO b = new RabbitBindingResponseDTO();
        b.setId(1L);
        b.setRoutingKey("order.*");
        when(adminService.getBindingById(1L)).thenReturn(b);

        ResponseEntity<ApiResponse<RabbitBindingResponseDTO>> response = controller.getBindingById(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("createBinding - returns 201 CREATED")
    void testCreateBinding() {
        RabbitBindingDTO dto = new RabbitBindingDTO();
        dto.setQueueId(1L);
        dto.setExchangeId(1L);
        dto.setRoutingKey("order.*");

        RabbitBindingResponseDTO created = new RabbitBindingResponseDTO();
        created.setId(1L);
        created.setRoutingKey("order.*");
        when(adminService.createBinding(any(RabbitBindingDTO.class))).thenReturn(created);

        ResponseEntity<ApiResponse<RabbitBindingResponseDTO>> response = controller.createBinding(dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getData().getRoutingKey()).isEqualTo("order.*");
    }

    @Test
    @DisplayName("updateBinding - returns 200 OK")
    void testUpdateBinding() {
        RabbitBindingDTO dto = new RabbitBindingDTO();
        dto.setRoutingKey("order.new.*");

        RabbitBindingResponseDTO updated = new RabbitBindingResponseDTO();
        updated.setId(1L);
        updated.setRoutingKey("order.new.*");
        when(adminService.updateBinding(eq(1L), any(RabbitBindingDTO.class))).thenReturn(updated);

        ResponseEntity<ApiResponse<RabbitBindingResponseDTO>> response = controller.updateBinding(1L, dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().getRoutingKey()).isEqualTo("order.new.*");
    }

    @Test
    @DisplayName("deleteBinding - returns 200 OK")
    void testDeleteBinding() {
        doNothing().when(adminService).deleteBinding(1L);

        ResponseEntity<ApiResponse<Void>> response = controller.deleteBinding(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(adminService).deleteBinding(1L);
    }

    // --- SYNC ---

    @Test
    @DisplayName("syncTopology - calls topologyService and returns 200 OK")
    void testSyncTopology() {
        doNothing().when(topologyService).resyncTopology();

        ResponseEntity<ApiResponse<Void>> response = controller.syncTopology();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getMessage()).contains("resincronizada");
        verify(topologyService).resyncTopology();
    }
}
