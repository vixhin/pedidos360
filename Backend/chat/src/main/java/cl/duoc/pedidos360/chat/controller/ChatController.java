package cl.duoc.pedidos360.chat.controller;

import cl.duoc.pedidos360.chat.dto.*;
import cl.duoc.pedidos360.chat.security.JwtUtil;
import cl.duoc.pedidos360.chat.service.ChatService;
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
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatService chatService;
    private final JwtUtil jwtUtil;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${bff.internal-key:${BFF_INTERNAL_KEY:pedidos360-internal-secret-key-local-2026}}")
    private String bffInternalKey;

    @Value("${services.usuario-url:${USUARIO_SERVICE_URL:http://localhost:8081}}")
    private String usuarioServiceUrl;

    public ChatController(ChatService chatService, JwtUtil jwtUtil) {
        this.chatService = chatService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/conversacion")
    public ResponseEntity<?> crearOObtenerConversacion(
            @RequestParam Long pedidoId,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) Long repartidorId,
            HttpServletRequest request) {
        try {
            UserIdentity identity = resolveIdentity(request);
            ConversacionResponseDTO response = chatService.crearOObtenerConversacion(pedidoId, clienteId, repartidorId, identity.userId, identity.role);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (SecurityException se) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", se.getMessage()));
        } catch (IllegalStateException ise) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("success", false, "message", ise.getMessage()));
        } catch (IllegalArgumentException ie) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "message", ie.getMessage()));
        }
    }

    @GetMapping("/pedido/{pedidoId}")
    public ResponseEntity<?> obtenerPorPedidoId(
            @PathVariable Long pedidoId,
            HttpServletRequest request) {
        try {
            UserIdentity identity = resolveIdentity(request);
            ConversacionResponseDTO response = chatService.obtenerPorPedidoId(pedidoId, identity.userId);
            return ResponseEntity.ok(response);
        } catch (SecurityException se) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", se.getMessage()));
        } catch (IllegalArgumentException ie) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", ie.getMessage()));
        }
    }

    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<?> obtenerConversacionesPorUsuario(
            @PathVariable Long usuarioId,
            HttpServletRequest request) {
        try {
            UserIdentity identity = resolveIdentity(request);
            return ResponseEntity.ok(chatService.obtenerConversacionesPorUsuario(identity.userId));
        } catch (SecurityException se) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", se.getMessage()));
        }
    }

    @PostMapping("/mensaje")
    public ResponseEntity<?> enviarMensaje(
            @RequestBody EnviarMensajeDTO dto,
            HttpServletRequest request) {
        try {
            UserIdentity identity = resolveIdentity(request);
            MensajeResponseDTO msg = chatService.enviarMensaje(dto, identity.userId, identity.role);
            return ResponseEntity.status(HttpStatus.CREATED).body(msg);
        } catch (SecurityException se) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", se.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException ie) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "message", ie.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    @DeleteMapping("/conversacion/{id}")
    public ResponseEntity<?> eliminarLogico(
            @PathVariable Long id,
            HttpServletRequest request) {
        try {
            UserIdentity identity = resolveIdentity(request);
            chatService.eliminarLogico(id, identity.userId);
            return ResponseEntity.ok(Map.of("success", true, "message", "Conversación ocultada exitosamente"));
        } catch (SecurityException se) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", se.getMessage()));
        }
    }

    private UserIdentity resolveIdentity(HttpServletRequest request) {
        String serviceKey = request.getHeader("X-Internal-Service-Key");
        String xUserId = request.getHeader("X-User-Id");
        String xUserEmail = request.getHeader("X-User-Email");
        String xUserRoles = request.getHeader("X-User-Roles");
        String authHeader = request.getHeader("Authorization");

        if (bffInternalKey != null && bffInternalKey.equals(serviceKey)) {
            Long userId = null;
            if (xUserId != null && !xUserId.isBlank()) {
                try { userId = Long.parseLong(xUserId); } catch (NumberFormatException ignored) {}
            }
            if (userId == null && xUserEmail != null && !xUserEmail.isBlank()) {
                userId = resolveUserIdByEmail(xUserEmail);
            }
            if (userId == null && request.getHeader("X-User-Sub") != null) {
                userId = resolveUserIdByEmail(request.getHeader("X-User-Sub"));
            }
            if (userId == null) {
                throw new SecurityException("Acceso denegado: No se pudo resolver la identidad del usuario desde BFF.");
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
                    throw new SecurityException("Acceso denegado: No se pudo resolver la cuenta de usuario.");
                }
                return new UserIdentity(userId, email, role != null ? role : "CLIENTE");
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
            log.warn("[CHAT-CONTROLLER] Could not resolve userId by email={}: {}", emailOrId, e.getMessage());
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
