package cl.duoc.pedidos360.pedidos.controller;

import cl.duoc.pedidos360.pedidos.entity.Pedido;
import cl.duoc.pedidos360.pedidos.security.JwtUtil;
import cl.duoc.pedidos360.pedidos.service.PedidoService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private static final Logger log = LoggerFactory.getLogger(PedidoController.class);

    private final PedidoService pedidoService;
    private final JwtUtil jwtUtil;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${bff.internal-key:${BFF_INTERNAL_KEY:pedidos360-internal-secret-key-local-2026}}")
    private String bffInternalKey;

    @Value("${services.usuario-url:${USUARIO_SERVICE_URL:http://localhost:8081}}")
    private String usuarioServiceUrl;

    public PedidoController(PedidoService pedidoService, JwtUtil jwtUtil) {
        this.pedidoService = pedidoService;
        this.jwtUtil = jwtUtil;
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
            HttpServletRequest request) {
        try {
            UserIdentity identity = resolveIdentity(request);
            Long callerUserId = identity.userId;
            String callerRoles = identity.role;
            return ResponseEntity.ok(pedidoService.actualizarEstado(id, nuevoEstado, callerUserId, callerRoles));
        } catch (SecurityException se) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", se.getMessage()));
        } catch (IllegalArgumentException ie) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", ie.getMessage()));
        }
    }

    @PutMapping("/{id}/repartidor")
    public ResponseEntity<?> asignarRepartidor(
            @PathVariable Long id,
            @RequestParam Long repartidorId,
            @RequestParam(defaultValue = "Repartidor") String nombreRepartidor,
            HttpServletRequest request) {
        try {
            UserIdentity identity = resolveIdentity(request);
            Long callerUserId = identity.userId;
            String callerRoles = identity.role;

            Long effectiveRepartidorId = repartidorId;
            if (callerRoles != null && callerRoles.toUpperCase().contains("REPARTIDOR") && callerUserId != null) {
                effectiveRepartidorId = callerUserId;
            }

            return ResponseEntity.ok(pedidoService.asignarRepartidor(id, effectiveRepartidorId, nombreRepartidor));
        } catch (IllegalStateException ise) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("success", false, "message", ise.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        pedidoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    private UserIdentity resolveIdentity(HttpServletRequest request) {
        String serviceKey = request.getHeader("X-Internal-Service-Key");
        String xUserId = request.getHeader("X-User-Id");
        String xUserEmail = request.getHeader("X-User-Email");
        String xUserRoles = request.getHeader("X-User-Roles");
        String authHeader = request.getHeader("Authorization");

        if (bffInternalKey != null && bffInternalKey.equals(serviceKey)) {
            Long userId = null;
            if (xUserEmail != null && !xUserEmail.isBlank()) {
                userId = resolveUserIdByEmail(xUserEmail);
            }
            if (userId == null && xUserId != null && !xUserId.isBlank()) {
                try { userId = Long.parseLong(xUserId); } catch (NumberFormatException ignored) {}
            }
            return new UserIdentity(userId, xUserEmail, xUserRoles != null ? xUserRoles : "CLIENTE");
        }

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtUtil.validateToken(token)) {
                String email = jwtUtil.extractEmail(token);
                String role = jwtUtil.extractRole(token);
                Long userId = resolveUserIdByEmail(email);
                return new UserIdentity(userId, email, role != null ? role : "CLIENTE");
            }
        }

        return new UserIdentity(null, null, "ANONYMOUS");
    }

    private Long resolveUserIdByEmail(String emailOrId) {
        if (emailOrId == null || emailOrId.isBlank()) return null;
        try {
            return Long.parseLong(emailOrId);
        } catch (NumberFormatException ignored) {}

        try {
            String url = usuarioServiceUrl + "/api/internal/usuario/by-email?email=" + emailOrId;
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Service-Key", bffInternalKey);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map data = (Map) response.getBody().get("data");
                if (data != null && data.get("id") != null) {
                    return ((Number) data.get("id")).longValue();
                }
            }
        } catch (Exception e) {
            log.warn("[PEDIDOS-CONTROLLER] Could not resolve userId by email={}: {}", emailOrId, e.getMessage());
        }
        return null;
    }

    private static class UserIdentity {
        final Long userId;
        final String email;
        final String role;

        UserIdentity(Long userId, String email, String role) {
            this.userId = userId;
            this.email = email;
            this.role = role;
        }
    }
}
