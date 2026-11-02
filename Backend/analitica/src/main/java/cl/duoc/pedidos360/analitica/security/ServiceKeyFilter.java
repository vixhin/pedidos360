package cl.duoc.pedidos360.analitica.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ServiceKeyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ServiceKeyFilter.class);

    @Value("${bff.internal-key:${BFF_INTERNAL_KEY:}}")
    private String bffInternalKey;

    @Value("${dev.auto-dev-admin:false}")
    private boolean autoDevAdmin;

    private final JwtUtil jwtUtil;

    public ServiceKeyFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        if ("OPTIONS".equalsIgnoreCase(method)
                || path.startsWith("/health")
                || path.startsWith("/ready")
                || path.startsWith("/actuator/health")) {
            filterChain.doFilter(request, response);
            return;
        }

        String serviceKey = request.getHeader("X-Internal-Service-Key");
        boolean isInternal = false;

        if (StringUtils.hasText(serviceKey) && StringUtils.hasText(bffInternalKey)
                && MessageDigest.isEqual(serviceKey.getBytes(StandardCharsets.UTF_8), bffInternalKey.getBytes(StandardCharsets.UTF_8))) {
            isInternal = true;
            String uid = request.getHeader("X-User-Id");
            if (StringUtils.hasText(uid)) {
                try {
                    request.setAttribute("p360.userId", Long.parseLong(uid.trim()));
                } catch (NumberFormatException ignored) {}
            }
            request.setAttribute("p360.email", request.getHeader("X-User-Email"));
            String roleHeader = request.getHeader("X-User-Role");
            if (!StringUtils.hasText(roleHeader)) {
                roleHeader = request.getHeader("X-User-Roles");
            }
            request.setAttribute("p360.role", roleHeader != null ? roleHeader : "CLIENTE");
        }

        String token = parseJwt(request);
        boolean fromCookie = false;
        if (token == null) {
            token = getCookieValue(request, "p360_session");
            if (token != null) fromCookie = true;
        }

        if (token != null) {
            if (jwtUtil.validateToken(token)) {
                if (fromCookie && isMutating(method)) {
                    if (!isValidCsrfToken(request)) {
                        log.warn("[ANALITICA-SECURITY][CSRF] Token CSRF ausente o inválido para path={}", path);
                        sendError(request, response, HttpServletResponse.SC_FORBIDDEN, "Acceso denegado: Token CSRF inválido o faltante.");
                        return;
                    }
                }

                Long uid = jwtUtil.extractUserId(token);
                String email = jwtUtil.extractEmail(token);
                String role = jwtUtil.extractRole(token);

                request.setAttribute("p360.userId", uid);
                request.setAttribute("p360.email", email);
                request.setAttribute("p360.role", role);
            } else {
                log.warn("[ANALITICA-SECURITY] Token JWT inválido para path={}", path);
                sendError(request, response, HttpServletResponse.SC_UNAUTHORIZED, "Token JWT inválido o expirado");
                return;
            }
        } else if (!isInternal && autoDevAdmin) {
            request.setAttribute("p360.userId", 1L);
            request.setAttribute("p360.email", "dev-admin@pedidos360.cl");
            request.setAttribute("p360.role", "ADMIN");
        }

        // Restricción a rol ADMIN para analítica
        String effectiveRole = (String) request.getAttribute("p360.role");
        boolean isAdmin = isInternal || (effectiveRole != null && (effectiveRole.contains("ADMIN") || "ADMIN".equalsIgnoreCase(effectiveRole)));

        if (!isAdmin) {
            sendError(request, response, HttpServletResponse.SC_FORBIDDEN, "Acceso restringido: Solo administradores pueden consultar o emitir analítica.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String parseJwt(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (StringUtils.hasText(auth) && auth.startsWith("Bearer ")) {
            return auth.substring(7).trim();
        }
        return null;
    }

    private String getCookieValue(HttpServletRequest req, String name) {
        Cookie[] cookies = req.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (name.equals(c.getName())) {
                    return c.getValue();
                }
            }
        }
        return null;
    }

    private boolean isMutating(String method) {
        return "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method);
    }

    private boolean isValidCsrfToken(HttpServletRequest request) {
        String headerCsrf = request.getHeader("X-XSRF-TOKEN");
        String cookieCsrf = getCookieValue(request, "XSRF-TOKEN");
        if (headerCsrf == null || cookieCsrf == null || headerCsrf.isBlank() || cookieCsrf.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                headerCsrf.trim().getBytes(StandardCharsets.UTF_8),
                cookieCsrf.trim().getBytes(StandardCharsets.UTF_8)
        );
    }

    private void sendError(HttpServletRequest req, HttpServletResponse res, int status, String msg) throws IOException {
        String origin = req.getHeader("Origin");
        if (origin != null && !origin.isBlank()) {
            res.setHeader("Access-Control-Allow-Origin", origin);
            res.setHeader("Access-Control-Allow-Credentials", "true");
            res.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
            res.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, X-Requested-With, Accept, Origin, Access-Control-Request-Method, Access-Control-Request-Headers, X-XSRF-TOKEN, X-Internal-Service-Key");
        }
        res.setStatus(status);
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write(String.format("{\"success\":false,\"message\":\"%s\",\"data\":null}", msg));
    }
}

