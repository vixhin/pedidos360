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
    public ResponseEntity<?> listar(HttpServletRequest request) {
        try {
            UserIdentity identity = resolveIdentity(request);
            String role = identity.getRoleUpper();
            if (role.contains("ADMIN") || role.contains("VENDEDOR")) {
                return ResponseEntity.ok(pedidoService.obtenerTodos());
            } else if (role.contains("REPARTIDOR")) {
                return ResponseEntity.ok(pedidoService.obtenerDisponiblesOAsignados(identity.userId));
            } else {
                return ResponseEntity.ok(pedidoService.obtenerPorUsuario(identity.userId));
            }
        } catch (SecurityException se) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", se.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerPorId(@PathVariable Long id, HttpServletRequest request) {
        try {
            UserIdentity identity = resolveIdentity(request);
            return pedidoService.obtenerPorId(id)
                    .map(pedido -> {
                        if (!puedeVerPedido(identity, pedido)) {
                            throw new SecurityException("Acceso denegado: No tiene permisos para ver este pedido.");
                        }
                        return ResponseEntity.ok(pedido);
                    })
                    .orElse(ResponseEntity.notFound().build());
        } catch (SecurityException se) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", se.getMessage()));
        }
    }

    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<?> obtenerPorUsuario(@PathVariable Long usuarioId, HttpServletRequest request) {
        try {
            UserIdentity identity = resolveIdentity(request);
            validarPuedeConsultarUsuario(identity, usuarioId);
            return ResponseEntity.ok(pedidoService.obtenerPorUsuario(usuarioId));
        } catch (SecurityException se) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", se.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody Pedido pedido, HttpServletRequest request) {
        try {
            UserIdentity identity = resolveIdentity(request);
            validarPuedeCrearPedido(identity, pedido);
            return ResponseEntity.ok(pedidoService.guardar(pedido));
        } catch (SecurityException se) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", se.getMessage()));
        }
    }

    @PutMapping("/{id}/estado")
    public ResponseEntity<?> actualizarEstado(
            @PathVariable Long id,
            @RequestParam String nuevoEstado,
            HttpServletRequest request) {
        try {
            UserIdentity identity = resolveIdentity(request);
            Pedido pedido = pedidoService.obtenerPorId(id)
                    .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado con ID: " + id));

            validarPuedeActualizarEstado(identity, pedido);
            return ResponseEntity.ok(pedidoService.actualizarEstado(id, nuevoEstado, identity.userId, identity.role));
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
            Pedido pedido = pedidoService.obtenerPorId(id)
                    .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado con ID: " + id));

            Long effectiveRepartidorId = validarPuedeAsignarRepartidor(identity, pedido, repartidorId);

            return ResponseEntity.ok(pedidoService.asignarRepartidor(id, effectiveRepartidorId, nombreRepartidor));
        } catch (SecurityException se) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", se.getMessage()));
        } catch (IllegalStateException ise) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("success", false, "message", ise.getMessage()));
        } catch (IllegalArgumentException ie) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", ie.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable Long id, HttpServletRequest request) {
        try {
            UserIdentity identity = resolveIdentity(request);
            validarPuedeEliminar(identity);
            pedidoService.eliminar(id);
            return ResponseEntity.noContent().build();
        } catch (SecurityException se) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", se.getMessage()));
        }
    }

    private void validarPuedeCrearPedido(UserIdentity identity, Pedido pedido) {
        String role = identity.getRoleUpper();
        if (role.contains("CLIENTE") || (!role.contains("ADMIN") && !role.contains("VENDEDOR"))) {
            pedido.setUsuarioId(identity.userId);
        } else {
            if (pedido.getUsuarioId() == null) {
                pedido.setUsuarioId(identity.userId);
            }
        }
    }

    private void validarPuedeActualizarEstado(UserIdentity identity, Pedido pedido) {
        String role = identity.getRoleUpper();
        if (role.contains("CLIENTE")) {
            throw new SecurityException("Acceso denegado: El cliente no puede modificar el estado del pedido.");
        }
        if (role.contains("REPARTIDOR") && !role.contains("ADMIN") && !role.contains("VENDEDOR")) {
            if (pedido.getRepartidorId() == null || !pedido.getRepartidorId().equals(identity.userId)) {
                throw new SecurityException("Acceso denegado: El repartidor no puede modificar pedidos asignados a otros repartidores.");
            }
        }
    }

    private Long validarPuedeAsignarRepartidor(UserIdentity identity, Pedido pedido, Long requestedRepartidorId) {
        String role = identity.getRoleUpper();
        if (role.contains("CLIENTE")) {
            throw new SecurityException("Acceso denegado: El cliente no puede asignar repartidores.");
        }
        Long effectiveRepartidorId = requestedRepartidorId;
        if (role.contains("REPARTIDOR") && !role.contains("ADMIN") && !role.contains("VENDEDOR")) {
            effectiveRepartidorId = identity.userId;
        }
        if (pedido.getRepartidorId() != null && !pedido.getRepartidorId().equals(effectiveRepartidorId)) {
            throw new IllegalStateException("El pedido ya fue asignado a otro repartidor.");
        }
        return effectiveRepartidorId;
    }

    private void validarPuedeEliminar(UserIdentity identity) {
        String role = identity.getRoleUpper();
        if (!role.contains("ADMIN")) {
            throw new SecurityException("Acceso denegado: No tiene permisos para eliminar pedidos.");
        }
    }

    private void validarPuedeConsultarUsuario(UserIdentity identity, Long usuarioIdTarget) {
        String role = identity.getRoleUpper();
        if (!role.contains("ADMIN") && !role.contains("VENDEDOR")) {
            if (!identity.userId.equals(usuarioIdTarget)) {
                throw new SecurityException("Acceso denegado: No puede consultar los pedidos de otro usuario.");
            }
        }
    }

    private boolean puedeVerPedido(UserIdentity identity, Pedido pedido) {
        String role = identity.getRoleUpper();
        if (role.contains("ADMIN") || role.contains("VENDEDOR")) {
            return true;
        }
        if (role.contains("REPARTIDOR")) {
            return pedido.getRepartidorId() == null || identity.userId.equals(pedido.getRepartidorId());
        }
        return identity.userId.equals(pedido.getUsuarioId());
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
            if (userId == null) {
                throw new SecurityException("Acceso denegado: No se pudo resolver la identidad del usuario desde la clave de servicio interno.");
            }
            return new UserIdentity(userId, xUserEmail, xUserRoles != null ? xUserRoles : "CLIENTE");
        }

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtUtil.validateToken(token)) {
                String email = jwtUtil.extractEmail(token);
                String role = jwtUtil.extractRole(token);
                Long userId = resolveUserIdByEmail(email);
                if (userId == null) {
                    throw new SecurityException("Acceso denegado: No se encontró usuario registrado para la cuenta del token.");
                }
                return new UserIdentity(userId, email, role != null ? role : "CLIENTE");
            } else {
                throw new SecurityException("Token JWT inválido o expirado.");
            }
        }

        throw new SecurityException("Acceso denegado: Requiere autenticación JWT válida o clave interna de servicio.");
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

        String getRoleUpper() {
            return role != null ? role.toUpperCase() : "";
        }
    }
}
