package cl.duoc.pedidos360.pedidos.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class ServiceKeyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ServiceKeyFilter.class);

    @Value("${bff.internal-key:${BFF_INTERNAL_KEY:pedidos360-internal-secret-key-local-2026}}")
    private String bffInternalKey;

    private final JwtUtil jwtUtil;

    public ServiceKeyFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Allow preflight, health checks, actuator
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || path.startsWith("/health") || path.startsWith("/actuator")) {
            filterChain.doFilter(request, response);
            return;
        }

        String serviceKey = request.getHeader("X-Internal-Service-Key");
        String authHeader = request.getHeader("Authorization");

        if (bffInternalKey != null && !bffInternalKey.isBlank()) {
            if (serviceKey != null && !serviceKey.isBlank()) {
                if (!bffInternalKey.equals(serviceKey)) {
                    log.warn("[PEDIDOS-SECURITY] Invalid S2S key provided for path={}", path);
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"success\":false,\"message\":\"Acceso denegado: Clave interna de servicio inválida.\"}");
                    return;
                }
            } else {
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    log.warn("[PEDIDOS-SECURITY] Missing internal S2S key and missing JWT auth for path={}", path);
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"success\":false,\"message\":\"Acceso denegado: Requiere autenticación JWT o clave interna de servicio.\"}");
                    return;
                }

                String token = authHeader.substring(7);
                if (!jwtUtil.validateToken(token)) {
                    log.warn("[PEDIDOS-SECURITY] Invalid or expired JWT token for path={}", path);
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"success\":false,\"message\":\"Acceso denegado: Token JWT inválido o expirado.\"}");
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
