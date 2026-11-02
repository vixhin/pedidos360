package cl.duoc.pedidos360.pedidos.controller;

import cl.duoc.pedidos360.pedidos.entity.ConfiguracionComercial;
import cl.duoc.pedidos360.pedidos.entity.Cupon;
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
import org.springframework.util.StringUtils;
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
    private final cl.duoc.pedidos360.pedidos.service.MotorComercialService motorComercialService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${bff.internal-key:${BFF_INTERNAL_KEY:pedidos360-internal-secret-key-local-2026}}")
    private String bffInternalKey;

    @Value("${services.usuario-url:${USUARIO_SERVICE_URL:http://localhost:8081}}")
    private String usuarioServiceUrl;

    public PedidoController(PedidoService pedidoService, JwtUtil jwtUtil, @org.springframework.beans.factory.annotation.Autowired(required = false) cl.duoc.pedidos360.pedidos.service.MotorComercialService motorComercialService) {
        this.pedidoService = pedidoService;
        this.jwtUtil = jwtUtil;
        this.motorComercialService = motorComercialService;
    }

    @PostMapping("/cotizar")
    public ResponseEntity<?> cotizar(@RequestBody cl.duoc.pedidos360.pedidos.dto.CotizacionRequestDTO request) {
        if (motorComercialService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Motor comercial no disponible");
        }
        return ResponseEntity.ok(motorComercialService.cotizar(request));
    }

    @GetMapping("/cobertura")
    public ResponseEntity<?> obtenerCobertura() {
        if (motorComercialService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Motor comercial no disponible");
        }
        return ResponseEntity.ok(motorComercialService.obtenerComunasActivas());
    }

    @GetMapping("/cupones/activos")
    public ResponseEntity<?> listarCuponesActivos() {
        if (motorComercialService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Motor comercial no disponible");
        }
        return ResponseEntity.ok(motorComercialService.listarCupones(true));
    }

    @GetMapping("/cupones")
    public ResponseEntity<?> listarTodosCupones() {
        if (motorComercialService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Motor comercial no disponible");
        }
        return ResponseEntity.ok(motorComercialService.listarCupones(false));
    }

    @PostMapping("/cupones")
    public ResponseEntity<?> crearCupon(@RequestBody Cupon cupon) {
        if (motorComercialService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Motor comercial no disponible");
        }
        return ResponseEntity.ok(motorComercialService.crearCupon(cupon));
    }

    @PutMapping("/cupones/{id}/toggle")
    public ResponseEntity<?> toggleCupon(@PathVariable Long id) {
        if (motorComercialService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Motor comercial no disponible");
        }
        return ResponseEntity.ok(motorComercialService.toggleCupon(id));
    }

    @GetMapping("/configuracion")
    public ResponseEntity<?> listarConfiguraciones() {
        if (motorComercialService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Motor comercial no disponible");
        }
        return ResponseEntity.ok(motorComercialService.listarConfiguraciones());
    }

    @PutMapping("/configuracion")
    public ResponseEntity<?> actualizarConfiguracion(@RequestBody Map<String, Object> body) {
        if (motorComercialService == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Motor comercial no disponible");
        }
        String clave = (String) body.get("clave");
        Object valorObj = body.get("valor");
        java.math.BigDecimal valor = valorObj != null ? new java.math.BigDecimal(valorObj.toString()) : java.math.BigDecimal.ZERO;
        return ResponseEntity.ok(motorComercialService.actualizarConfiguracion(clave, valor));
    }

    @GetMapping
    public ResponseEntity<?> listar(HttpServletRequest request) {
        try {
            UserIdentity identity = resolveIdentity(request);
            String role = identity.getRoleUpper();
            if (role.contains("REPARTIDOR")) {
                return ResponseEntity.ok(pedidoService.obtenerDisponiblesOAsignados(identity.userId));
            }
            if (role.contains("ADMIN") || role.contains("VENDEDOR")) {
                return ResponseEntity.ok(pedidoService.obtenerTodos());
            }
            return ResponseEntity.ok(pedidoService.obtenerPorUsuario(identity.userId));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", e.getMessage()));
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
            String idempotencyKey = request.getHeader("Idempotency-Key");
            if (StringUtils.hasText(idempotencyKey)) {
                pedido.setIdempotencyKey(idempotencyKey.trim());
            }
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
            return ResponseEntity.ok().build();
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
        Long userId = (Long) request.getAttribute("p360.userId");
        String email = (String) request.getAttribute("p360.email");
        String role = (String) request.getAttribute("p360.role");

        if (userId != null) {
            return new UserIdentity(userId, email, role != null ? role : "CLIENTE");
        }

        String serviceKey = request.getHeader("X-Internal-Service-Key");
        if (StringUtils.hasText(bffInternalKey) && StringUtils.hasText(serviceKey) && bffInternalKey.equals(serviceKey)) {
            String xUserId = request.getHeader("X-User-Id");
            if (StringUtils.hasText(xUserId)) {
                try {
                    return new UserIdentity(Long.parseLong(xUserId), request.getHeader("X-User-Email"), request.getHeader("X-User-Role"));
                } catch (NumberFormatException ignored) {}
            }
        }

        throw new SecurityException("Acceso denegado: Requiere autenticación JWT válida o sesión activa.");
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
