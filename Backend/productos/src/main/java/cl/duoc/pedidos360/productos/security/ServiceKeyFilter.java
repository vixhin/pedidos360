package cl.duoc.pedidos360.productos.security;

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

/**
 * Autenticación y autorización de productos-service.
 *
 * <ul>
 *   <li>Lectura del catálogo (GET): pública, salvo los favoritos de un usuario.</li>
 *   <li>Crear, editar y eliminar productos: sólo ADMIN o VENDEDOR.</li>
 *   <li>Importar catálogo semilla: sólo ADMIN.</li>
 *   <li>Descontar stock: sólo comunicación interna entre servicios (X-Internal-Service-Key).</li>
 *   <li>Favoritos: usuario autenticado y dueño de los favoritos (o ADMIN).</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ServiceKeyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ServiceKeyFilter.class);
    private static final String BASE = "/api/productos";
    private static final String FAVORITOS = BASE + "/favoritos";

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

        if ("OPTIONS".equalsIgnoreCase(method) || !path.startsWith(BASE)) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean isInternal = esLlaveInterna(request.getHeader("X-Internal-Service-Key"));
        if (isInternal) {
            request.setAttribute("p360.internal", Boolean.TRUE);
        }

        String token = parseJwt(request);
        boolean fromCookie = false;
        if (token == null) {
            token = getCookieValue(request, "p360_session");
            fromCookie = token != null;
        }

        if (token != null) {
            if (!jwtUtil.validateToken(token)) {
                log.warn("[PRODUCTOS-SECURITY] Token JWT inválido para path={}", path);
                sendError(request, response, HttpServletResponse.SC_UNAUTHORIZED, "Token JWT inválido o expirado");
                return;
            }
            if (fromCookie && isMutating(method) && !isValidCsrfToken(request)) {
                log.warn("[PRODUCTOS-SECURITY][CSRF] Token CSRF ausente o inválido para path={}", path);
                sendError(request, response, HttpServletResponse.SC_FORBIDDEN, "Acceso denegado: Token CSRF inválido o faltante.");
                return;
            }
            request.setAttribute("p360.userId", jwtUtil.extractUserId(token));
            request.setAttribute("p360.email", jwtUtil.extractEmail(token));
            request.setAttribute("p360.role", jwtUtil.extractRole(token));
        } else if (!isInternal && autoDevAdmin) {
            // Sólo perfil dev: permite usar el frontend local sin iniciar sesión.
            request.setAttribute("p360.userId", 1L);
            request.setAttribute("p360.email", "dev-admin@pedidos360.cl");
            request.setAttribute("p360.role", "ADMIN");
        }

        String rechazo = autorizar(path, method, isInternal, request);
        if (rechazo != null) {
            int status = request.getAttribute("p360.role") == null && !isInternal
                    ? HttpServletResponse.SC_UNAUTHORIZED
                    : HttpServletResponse.SC_FORBIDDEN;
            log.warn("[PRODUCTOS-SECURITY] {} {} rechazado: {}", method, path, rechazo);
            sendError(request, response, status, rechazo);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /** Devuelve el motivo del rechazo, o null si la petición está autorizada. */
    private String autorizar(String path, String method, boolean isInternal, HttpServletRequest request) {
        if (isInternal) {
            return null;
        }
        String role = (String) request.getAttribute("p360.role");
        Long userId = (Long) request.getAttribute("p360.userId");
        boolean autenticado = role != null;
        boolean admin = autenticado && role.toUpperCase().contains("ADMIN");
        boolean gestor = admin || (autenticado && role.toUpperCase().contains("VENDEDOR"));

        if (path.startsWith(FAVORITOS)) {
            if (!autenticado || userId == null) {
                return "Se requiere autenticación para gestionar favoritos.";
            }
            Long dueno = usuarioIdEnRuta(path);
            if (dueno != null && !admin && !dueno.equals(userId)) {
                return "Acceso denegado: no puede acceder a los favoritos de otro usuario.";
            }
            return null;
        }

        if ("GET".equalsIgnoreCase(method)) {
            return null;
        }
        if (path.matches(BASE + "/\\d+/stock/descontar")) {
            return "El descuento de stock sólo está disponible para servicios internos.";
        }
        if (path.startsWith(BASE + "/dev/")) {
            return admin ? null : "Acceso restringido a administradores.";
        }
        if (!autenticado) {
            return "Se requiere autenticación.";
        }
        return gestor ? null : "Acceso restringido a administradores o vendedores.";
    }

    /** Extrae el usuarioId de /api/productos/favoritos/{usuarioId}[/{productoId}]. */
    private Long usuarioIdEnRuta(String path) {
        String resto = path.substring(FAVORITOS.length());
        if (resto.startsWith("/")) {
            String[] partes = resto.substring(1).split("/");
            try {
                return Long.parseLong(partes[0]);
            } catch (NumberFormatException e) {
                return -1L;
            }
        }
        return null;
    }

    private boolean esLlaveInterna(String serviceKey) {
        return StringUtils.hasText(serviceKey) && StringUtils.hasText(bffInternalKey)
                && MessageDigest.isEqual(serviceKey.getBytes(StandardCharsets.UTF_8), bffInternalKey.getBytes(StandardCharsets.UTF_8));
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
                cookieCsrf.trim().getBytes(StandardCharsets.UTF_8));
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
