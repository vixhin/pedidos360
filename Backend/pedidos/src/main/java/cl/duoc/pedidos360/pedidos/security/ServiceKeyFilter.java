package cl.duoc.pedidos360.pedidos.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
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

        // 1. Siempre permitir OPTIONS (preflight de CORS), health, actuator y endpoints de clientes / cupones
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())
                || path.startsWith("/health")
                || path.startsWith("/actuator")
                || path.contains("/cupones")
                || path.contains("/configuracion")
                || path.contains("/cobertura")
                || path.contains("/cotizar")) {
            filterChain.doFilter(request, response);
            return;
        }

        String serviceKey = request.getHeader("X-Internal-Service-Key");
        String authHeader = request.getHeader("Authorization");

        // 2. Si viene desde BFF con la clave interna correcta
        if (serviceKey != null && !serviceKey.isBlank()) {
            if (bffInternalKey != null && bffInternalKey.equals(serviceKey)) {
                filterChain.doFilter(request, response);
                return;
            } else {
                log.warn("[PEDIDOS-SECURITY] Clave interna inválida para path={}", path);
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"success\":false,\"message\":\"Acceso denegado: Clave interna de servicio inválida.\"}");
                return;
            }
        }

        // 3. Si viene con token JWT local
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtUtil.validateToken(token)) {
                filterChain.doFilter(request, response);
                return;
            } else {
                log.warn("[PEDIDOS-SECURITY] Token JWT inválido para path={}", path);
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"success\":false,\"message\":\"Acceso denegado: Token JWT inválido o expirado.\"}");
                return;
            }
        }

        // 4. En desarrollo (localhost / frontend directo sin BFF levantado)
        String origin = request.getHeader("Origin");
        if (origin != null && origin.contains("localhost")) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"success\":false,\"message\":\"Acceso denegado: Requiere autenticación JWT o clave interna de servicio.\"}");
    }
}
