package cl.duoc.pedidos360.bff.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BffEntraSyncController — Endpoint seguro del BFF para sincronizar la identidad
 * de usuarios autenticados con Microsoft Entra ID hacia usuario-service.
 *
 * El frontend NO envía email ni rol como fuente de verdad.
 * La identidad se extrae exclusivamente del JWT validado por Spring Security:
 * - Email: preferred_username -> upn -> email -> subject
 * - Nombre: name claim (fallback: email)
 * - Rol: roles claim (ADMIN > VENDEDOR > CLIENTE, default: CLIENTE)
 */
@RestController
@RequestMapping("/api/bff/auth")
public class BffEntraSyncController {

    private static final Logger log = LoggerFactory.getLogger(BffEntraSyncController.class);

    private final WebClient usuarioClient;
    private final String internalKey;

    public BffEntraSyncController(
            @Qualifier("usuarioClient") WebClient usuarioClient,
            @Value("${bff.internal-key:pedidos360-internal-secret-key-2026}") String internalKey) {
        this.usuarioClient = usuarioClient;
        this.internalKey = internalKey;
    }

    @PostMapping("/entra-sync")
    public ResponseEntity<?> syncFromJwt(@AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Token JWT no proporcionado o inválido"));
        }

        // 1. Extraer Email
        String email = jwt.getClaimAsString("preferred_username");
        if (email == null || email.isBlank()) {
            email = jwt.getClaimAsString("upn");
        }
        if (email == null || email.isBlank()) {
            email = jwt.getClaimAsString("email");
        }
        if (email == null || email.isBlank()) {
            email = jwt.getSubject();
        }

        // 2. Extraer Nombre
        String nombre = jwt.getClaimAsString("name");
        if (nombre == null || nombre.isBlank()) {
            nombre = email;
        }

        // 3. Extraer Roles y determinar rol de la app
        List<String> roles = jwt.getClaimAsStringList("roles");
        String rol = resolveRoleFromClaims(roles);

        log.info("[BFF][ENTRA-SYNC] Identity extracted from Jwt: sub={} email={} name={} resolvedRole={}",
                jwt.getSubject(), email, nombre, rol);

        // 4. Invocar usuario-service mediante endpoint interno seguro
        Map<String, String> payload = new HashMap<>();
        payload.put("email", email);
        payload.put("nombre", nombre);
        payload.put("rol", rol);

        try {
            String responseBody = usuarioClient.post()
                    .uri("/api/internal/entra-sync")
                    .header("X-Internal-Service-Key", internalKey)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(responseBody);
        } catch (WebClientResponseException ex) {
            log.error("[BFF][ENTRA-SYNC] usuario-service returned error status={}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            return ResponseEntity.status(ex.getStatusCode())
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(ex.getResponseBodyAsString());
        } catch (Exception ex) {
            log.error("[BFF][ENTRA-SYNC] Error communicating with usuario-service: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("success", false, "message", "Error de comunicación con usuario-service: " + ex.getMessage()));
        }
    }

    private String resolveRoleFromClaims(List<String> roles) {
        if (roles != null && !roles.isEmpty()) {
            for (String r : roles) {
                if ("ADMIN".equalsIgnoreCase(r)) return "ADMIN";
            }
            for (String r : roles) {
                if ("VENDEDOR".equalsIgnoreCase(r)) return "VENDEDOR";
            }
            for (String r : roles) {
                if ("CLIENTE".equalsIgnoreCase(r)) return "CLIENTE";
            }
        }
        return "CLIENTE";
    }
}
