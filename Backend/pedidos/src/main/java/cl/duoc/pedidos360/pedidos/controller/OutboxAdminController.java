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

    @Value("${bff.internal-key:${BFF_INTERNAL_KEY:pedidos360-internal-secret-key-local-2026}}")
    private String bffInternalKey;

    public OutboxAdminController(OutboxService outboxService, JwtUtil jwtUtil) {
        this.outboxService = outboxService;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Endpoint administrativo protegido para reprocesar eventos en estado FAILED.
     * Requiere rol ADMIN o clave interna S2S (X-Internal-Service-Key).
     */
    @PostMapping("/reprocess-failed")
    public ResponseEntity<?> reprocessFailedEvents(@RequestParam(defaultValue = "50") int limit,
                                                   HttpServletRequest request) {
        if (!isAuthorizedAdmin(request)) {
            log.warn("[OUTBOX-ADMIN] Unauthorized attempt to reprocess failed outbox events from IP={}",
                    request.getRemoteAddr());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", "Acceso denegado: Se requiere rol ADMIN o clave de servicio interna."));
        }

        int count = outboxService.reprocessFailedEvents(Math.min(limit, 500));
        return ResponseEntity.ok(Map.of(
                "success", true,
                "reprocessedCount", count,
                "message", "Eventos fallidos reencolados a PENDING para reintento"
        ));
    }

    private boolean isAuthorizedAdmin(HttpServletRequest request) {
        String serviceKey = request.getHeader("X-Internal-Service-Key");
        if (bffInternalKey != null && bffInternalKey.equals(serviceKey)) {
            return true;
        }

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
