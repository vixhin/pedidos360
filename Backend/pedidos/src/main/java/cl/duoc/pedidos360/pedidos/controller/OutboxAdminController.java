package cl.duoc.pedidos360.pedidos.controller;

import cl.duoc.pedidos360.pedidos.outbox.service.OutboxService;
import cl.duoc.pedidos360.pedidos.security.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/pedidos/admin/outbox")
public class OutboxAdminController {

    private static final Logger log = LoggerFactory.getLogger(OutboxAdminController.class);

    private final OutboxService outboxService;
    private final JwtUtil jwtUtil;

    // Sin secretos predeterminados hardcodeados: se obtiene estrictamente de variables de entorno o configuración externa
    @Value("${bff.internal-key:${BFF_INTERNAL_KEY:}}")
    private String bffInternalKey;

    public OutboxAdminController(OutboxService outboxService, JwtUtil jwtUtil) {
        this.outboxService = outboxService;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Endpoint administrativo protegido para reprocesar eventos en estado FAILED.
     * Requiere rol ADMIN verificado vía JWT o clave interna entre servicios válida (no vacía).
     */
    @PostMapping("/reprocess-failed")
    public ResponseEntity<?> reprocessFailedEvents(@RequestParam(defaultValue = "50") int limit,
                                                   HttpServletRequest request) {
        // 1. Validación estricta del rango seguro de limit (1 a 500)
        if (limit < 1 || limit > 500) {
            log.warn("[OUTBOX-ADMIN] Invalid limit parameter provided: limit={} from IP={}", limit, request.getRemoteAddr());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "message", "El parámetro 'limit' debe ser un valor entero entre 1 y 500."));
        }

        // 2. Control de autenticación y autorización administrativa
        if (!isAuthorizedAdmin(request)) {
            log.warn("[OUTBOX-ADMIN] Unauthorized access attempt to reprocess-failed from IP={}", request.getRemoteAddr());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", "Acceso denegado: Se requiere rol ADMIN o autenticación interna autorizada."));
        }

        int count = outboxService.reprocessFailedEvents(limit);
        log.info("[OUTBOX-ADMIN] Administrative reprocessing triggered: {} failed event(s) reset to PENDING by caller from IP={}",
                count, request.getRemoteAddr());

        return ResponseEntity.ok(Map.of(
                "success", true,
                "reprocessedCount", count,
                "message", "Eventos fallidos reencolados a PENDING para reintento"
        ));
    }

    private boolean isAuthorizedAdmin(HttpServletRequest request) {
        // A. Verificación de clave interna entre servicios S2S (estrictamente no vacía)
        String serviceKey = request.getHeader("X-Internal-Service-Key");
        if (serviceKey != null && !serviceKey.trim().isEmpty() &&
                bffInternalKey != null && !bffInternalKey.trim().isEmpty() &&
                bffInternalKey.equals(serviceKey)) {
            return true;
        }

        // B. Verificación de autenticación JWT con rol ADMIN
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtUtil.validateToken(token)) {
                String role = jwtUtil.extractRole(token);
                return "ADMIN".equalsIgnoreCase(role);
            }
        }

        return false;
    }
}
