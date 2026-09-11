package cl.duoc.pedidos360.bff.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * BffSecurityExceptionHandler
 *
 * Maneja excepciones de seguridad del BFF y devuelve JSON en lugar de HTML.
 *
 * Implementa:
 * - AuthenticationEntryPoint → 401 Unauthorized
 * - AccessDeniedHandler      → 403 Forbidden
 *
 * Respuesta 401 (token ausente, inválido o expirado):
 * {
 *   "status": 401,
 *   "error": "UNAUTHORIZED",
 *   "message": "Token inválido, expirado o ausente",
 *   "timestamp": "2026-..."
 * }
 *
 * Respuesta 403 (token válido pero sin permisos):
 * {
 *   "status": 403,
 *   "error": "FORBIDDEN",
 *   "message": "No posee permisos suficientes",
 *   "timestamp": "2026-..."
 * }
 *
 * Logs:
 * [BFF][AUTH]     — 401: detalles del token inválido
 * [BFF][FORBIDDEN] — 403: usuario y ruta
 *
 * IMPORTANTE: Nunca se loggean tokens completos.
 */
@Component
public class BffSecurityExceptionHandler
        implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(BffSecurityExceptionHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── 401 Unauthorized ─────────────────────────────────────────────────────

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException)
            throws IOException, ServletException {

        // Diagnóstico: claims no verificados del token (aud/iss/ver) — NUNCA la firma
        String authHeader = request.getHeader("Authorization");
        String tokenPeek = "sin-Authorization";
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            tokenPeek = peekClaims(authHeader.substring(7));
        }

        log.warn("[BFF][AUTH] 401 Unauthorized | uri={} | reason={} | token[{}]",
                request.getRequestURI(),
                authException.getMessage(), // Solo mensaje, nunca el token
                tokenPeek);

        writeError(response, 401, "UNAUTHORIZED", "Token inválido, expirado o ausente");
    }

    /** Decodifica (sin verificar) el payload del JWT y devuelve aud/iss/ver para diagnóstico. */
    private String peekClaims(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) return "no-jwt";
            String json = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            Map<?, ?> claims = objectMapper.readValue(json, Map.class);
            return "aud=" + claims.get("aud") + " iss=" + claims.get("iss") + " ver=" + claims.get("ver")
                    + " appid=" + claims.get("appid") + " scp=" + claims.get("scp");
        } catch (Exception e) {
            return "no-decodificable";
        }
    }

    // ── 403 Forbidden ────────────────────────────────────────────────────────

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException)
            throws IOException, ServletException {

        // Extraer usuario del header de forma segura (sin imprimir el token)
        String authHeader = request.getHeader("Authorization");
        String userInfo = (authHeader != null && authHeader.startsWith("Bearer "))
                ? "Bearer <token-present>"
                : "no-auth-header";

        log.warn("[BFF][FORBIDDEN] 403 Forbidden | uri={} | auth={} | reason={}",
                request.getRequestURI(),
                userInfo,
                accessDeniedException.getMessage());

        writeError(response, 403, "FORBIDDEN", "No posee permisos suficientes");
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private void writeError(HttpServletResponse response,
                            int status,
                            String error,
                            String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("error", error);
        body.put("message", message);
        body.put("timestamp", Instant.now().toString());

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
