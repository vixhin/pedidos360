package cl.duoc.pedidos360.chat.security;

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

    @Value("${bff.internal-key:${BFF_INTERNAL_KEY:}}")
    private String bffInternalKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Skip filter for OPTIONS preflight, health checks, or websocket handshake
        if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || path.startsWith("/health") || path.startsWith("/ws-chat")) {
            filterChain.doFilter(request, response);
            return;
        }

        String serviceKey = request.getHeader("X-Internal-Service-Key");
        String authHeader = request.getHeader("Authorization");

        if (bffInternalKey != null && !bffInternalKey.isBlank()) {
            if (serviceKey != null && !serviceKey.isBlank()) {
                if (!bffInternalKey.equals(serviceKey)) {
                    log.warn("[CHAT-SECURITY] Invalid S2S key provided for path={}", path);
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"success\":false,\"message\":\"Acceso denegado: Clave interna de servicio inválida.\"}");
                    return;
                }
            } else if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("[CHAT-SECURITY] Missing internal S2S key and missing JWT auth for path={}", path);
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"success\":false,\"message\":\"Acceso denegado: Requiere autenticación o clave interna de servicio.\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
