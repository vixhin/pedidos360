package cl.duoc.pedidos360.usuario.controller;

import cl.duoc.pedidos360.usuario.dto.*;
import cl.duoc.pedidos360.usuario.service.RabbitAdminService;
import cl.duoc.pedidos360.usuario.service.RabbitTopologyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/rabbit")
@PreAuthorize("hasRole('ADMIN')")
public class RabbitAdminController {

    private final RabbitAdminService adminService;
    private final RabbitTopologyService topologyService;

    public RabbitAdminController(RabbitAdminService adminService, RabbitTopologyService topologyService) {
        this.adminService = adminService;
        this.topologyService = topologyService;
    }

    // =========================================================================
    // QUEUES
    // =========================================================================

    @GetMapping("/queues")
    public ResponseEntity<ApiResponse<List<RabbitQueueResponseDTO>>> listQueues() {
        List<RabbitQueueResponseDTO> queues = adminService.listQueues();
        return ResponseEntity.ok(ApiResponse.ok("Queues obtenidas exitosamente", queues));
    }

    @GetMapping("/queues/{id}")
    public ResponseEntity<ApiResponse<RabbitQueueResponseDTO>> getQueueById(@PathVariable Long id) {
        RabbitQueueResponseDTO queue = adminService.getQueueById(id);
        return ResponseEntity.ok(ApiResponse.ok("Queue obtenida exitosamente", queue));
    }

    @PostMapping("/queues")
    public ResponseEntity<ApiResponse<RabbitQueueResponseDTO>> createQueue(@Valid @RequestBody RabbitQueueDTO dto) {
        RabbitQueueResponseDTO created = adminService.createQueue(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Queue creada exitosamente", created));
    }

    @PutMapping("/queues/{id}")
    public ResponseEntity<ApiResponse<RabbitQueueResponseDTO>> updateQueue(@PathVariable Long id,
                                                                           @Valid @RequestBody RabbitQueueDTO dto) {
        RabbitQueueResponseDTO updated = adminService.updateQueue(id, dto);
        return ResponseEntity.ok(ApiResponse.ok("Queue actualizada exitosamente", updated));
    }

    @DeleteMapping("/queues/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteQueue(@PathVariable Long id) {
        adminService.deleteQueue(id);
        return ResponseEntity.ok(ApiResponse.ok("Queue eliminada exitosamente", null));
    }

    // =========================================================================
    // EXCHANGES
    // =========================================================================

    @GetMapping("/exchanges")
    public ResponseEntity<ApiResponse<List<RabbitExchangeResponseDTO>>> listExchanges() {
        List<RabbitExchangeResponseDTO> exchanges = adminService.listExchanges();
        return ResponseEntity.ok(ApiResponse.ok("Exchanges obtenidos exitosamente", exchanges));
    }

    @GetMapping("/exchanges/{id}")
    public ResponseEntity<ApiResponse<RabbitExchangeResponseDTO>> getExchangeById(@PathVariable Long id) {
        RabbitExchangeResponseDTO exchange = adminService.getExchangeById(id);
        return ResponseEntity.ok(ApiResponse.ok("Exchange obtenido exitosamente", exchange));
    }

    @PostMapping("/exchanges")
    public ResponseEntity<ApiResponse<RabbitExchangeResponseDTO>> createExchange(@Valid @RequestBody RabbitExchangeDTO dto) {
        RabbitExchangeResponseDTO created = adminService.createExchange(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Exchange creado exitosamente", created));
    }

    @PutMapping("/exchanges/{id}")
    public ResponseEntity<ApiResponse<RabbitExchangeResponseDTO>> updateExchange(@PathVariable Long id,
                                                                                 @Valid @RequestBody RabbitExchangeDTO dto) {
        RabbitExchangeResponseDTO updated = adminService.updateExchange(id, dto);
        return ResponseEntity.ok(ApiResponse.ok("Exchange actualizado exitosamente", updated));
    }

    @DeleteMapping("/exchanges/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteExchange(@PathVariable Long id) {
        adminService.deleteExchange(id);
        return ResponseEntity.ok(ApiResponse.ok("Exchange eliminado exitosamente", null));
    }

    // =========================================================================
    // BINDINGS
    // =========================================================================

    @GetMapping("/bindings")
    public ResponseEntity<ApiResponse<List<RabbitBindingResponseDTO>>> listBindings() {
        List<RabbitBindingResponseDTO> bindings = adminService.listBindings();
        return ResponseEntity.ok(ApiResponse.ok("Bindings obtenidos exitosamente", bindings));
    }

    @GetMapping("/bindings/{id}")
    public ResponseEntity<ApiResponse<RabbitBindingResponseDTO>> getBindingById(@PathVariable Long id) {
        RabbitBindingResponseDTO binding = adminService.getBindingById(id);
        return ResponseEntity.ok(ApiResponse.ok("Binding obtenido exitosamente", binding));
    }

    @PostMapping("/bindings")
    public ResponseEntity<ApiResponse<RabbitBindingResponseDTO>> createBinding(@Valid @RequestBody RabbitBindingDTO dto) {
        RabbitBindingResponseDTO created = adminService.createBinding(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Binding creado exitosamente", created));
    }

    @PutMapping("/bindings/{id}")
    public ResponseEntity<ApiResponse<RabbitBindingResponseDTO>> updateBinding(@PathVariable Long id,
                                                                               @Valid @RequestBody RabbitBindingDTO dto) {
        RabbitBindingResponseDTO updated = adminService.updateBinding(id, dto);
        return ResponseEntity.ok(ApiResponse.ok("Binding actualizado exitosamente", updated));
    }

    @DeleteMapping("/bindings/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteBinding(@PathVariable Long id) {
        adminService.deleteBinding(id);
        return ResponseEntity.ok(ApiResponse.ok("Binding eliminado exitosamente", null));
    }

    // =========================================================================
    // SYNC TOPOLOGY
    // =========================================================================

    @PostMapping("/sync")
    public ResponseEntity<ApiResponse<Void>> syncTopology() {
        topologyService.resyncTopology();
        return ResponseEntity.ok(ApiResponse.ok("Topología RabbitMQ resincronizada desde PostgreSQL exitosamente", null));
    }
}
