package cl.duoc.pedidos360.pedidos.controller;

import cl.duoc.pedidos360.pedidos.entity.Pedido;
import cl.duoc.pedidos360.pedidos.service.PedidoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @GetMapping
    public ResponseEntity<List<Pedido>> listar() {
        return ResponseEntity.ok(pedidoService.obtenerTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Pedido> obtenerPorId(@PathVariable Long id) {
        return pedidoService.obtenerPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<Pedido>> obtenerPorUsuario(@PathVariable Long usuarioId) {
        return ResponseEntity.ok(pedidoService.obtenerPorUsuario(usuarioId));
    }

    @PostMapping
    public ResponseEntity<Pedido> crear(@RequestBody Pedido pedido) {
        return ResponseEntity.ok(pedidoService.guardar(pedido));
    }

    @PutMapping("/{id}/estado")
    public ResponseEntity<?> actualizarEstado(
            @PathVariable Long id,
            @RequestParam String nuevoEstado,
            @RequestHeader(value = "X-User-Id", required = false) String xUserId,
            @RequestHeader(value = "X-User-Roles", required = false) String xUserRoles) {
        try {
            Long callerUserId = (xUserId != null && !xUserId.isBlank()) ? Long.parseLong(xUserId) : null;
            return ResponseEntity.ok(pedidoService.actualizarEstado(id, nuevoEstado, callerUserId, xUserRoles));
        } catch (SecurityException se) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                    .body(java.util.Map.of("success", false, "message", se.getMessage()));
        } catch (IllegalArgumentException ie) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND)
                    .body(java.util.Map.of("success", false, "message", ie.getMessage()));
        }
    }

    @PutMapping("/{id}/repartidor")
    public ResponseEntity<?> asignarRepartidor(
            @PathVariable Long id,
            @RequestParam Long repartidorId,
            @RequestParam(defaultValue = "Repartidor") String nombreRepartidor,
            @RequestHeader(value = "X-User-Id", required = false) String xUserId,
            @RequestHeader(value = "X-User-Roles", required = false) String xUserRoles) {
        try {
            Long effectiveRepartidorId = repartidorId;
            if (xUserRoles != null && xUserRoles.toUpperCase().contains("REPARTIDOR") && xUserId != null && !xUserId.isBlank()) {
                effectiveRepartidorId = Long.parseLong(xUserId);
            }
            return ResponseEntity.ok(pedidoService.asignarRepartidor(id, effectiveRepartidorId, nombreRepartidor));
        } catch (IllegalStateException ise) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT)
                    .body(java.util.Map.of("success", false, "message", ise.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        pedidoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
