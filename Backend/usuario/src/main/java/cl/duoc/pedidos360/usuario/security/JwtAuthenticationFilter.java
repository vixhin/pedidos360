package cl.duoc.pedidos360.usuario.security;

import cl.duoc.pedidos360.usuario.enums.Rol;
import cl.duoc.pedidos360.usuario.service.SessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtUtil jwtUtil;
    private final SessionService sessionService;

    @Value("${bff.internal-key:${BFF_INTERNAL_KEY:}}")
    private String internalKey;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, SessionService sessionService) {
        this.jwtUtil = jwtUtil;
        this.sessionService = sessionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = parseJwt(request);
        boolean fromCookie = false;

        if (token == null) {
            token = getCookieValue(request, "p360_session");
            if (token != null) {
                fromCookie = true;
            }
        }

        // 1. Si hay token JWT (Bearer o Cookie)
        if (token != null) {
            if (jwtUtil.validateToken(token)) {
                String sid = jwtUtil.extractSessionId(token);
                if (sid != null && !sessionService.esSesionValida(sid)) {
                    log.warn("[AUTH-FILTER] Sesión inválida o revocada: sid={}", sid);
                    sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Sesión inválida o revocada. Inicie sesión nuevamente.");
                    return;
                }

                // Validación CSRF si la petición proviene de cookie y es un método mutante
                if (fromCookie && isMutatingMethod(request.getMethod()) && requiresCsrfProtection(request.getRequestURI())) {
                    if (!isValidCsrfToken(request)) {
                        log.warn("[AUTH-FILTER][CSRF] Petición mutante con cookie rechazada: falta o no coincide token CSRF");
                        sendErrorResponse(response, HttpServletResponse.SC_FORBIDDEN, "Acceso denegado: Token CSRF inválido o faltante.");
                        return;
                    }
                }

                String email = jwtUtil.extractEmail(token);
                String role = jwtUtil.extractRole(token);
                Long uid = jwtUtil.extractUserId(token);

                SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(email, null, Collections.singletonList(authority));

                request.setAttribute("p360.userId", uid);
                request.setAttribute("p360.email", email);
                request.setAttribute("p360.role", role);
                request.setAttribute("p360.sid", sid);

                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                log.warn("[AUTH-FILTER] Token JWT expirado o con firma inválida");
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Token JWT inválido o expirado");
                return;
            }
        } else {
            // 2. Comunicación interna autenticada desde BFF (S2S)
            String clientKey = request.getHeader("X-Internal-Service-Key");
            if (StringUtils.hasText(internalKey) && StringUtils.hasText(clientKey)
                    && MessageDigest.isEqual(internalKey.getBytes(StandardCharsets.UTF_8), clientKey.getBytes(StandardCharsets.UTF_8))) {

                String email = request.getHeader("X-User-Email");
                if (!StringUtils.hasText(email)) {
                    email = request.getHeader("X-User-Sub");
                }
                String roleHeader = request.getHeader("X-User-Role");
                if (!StringUtils.hasText(roleHeader)) {
                    roleHeader = request.getHeader("X-User-Roles");
                }

                if (StringUtils.hasText(roleHeader)) {
                    Rol rol = parseRol(roleHeader);
                    SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + rol.name());
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(email != null ? email : "internal-service", null, List.of(authority));

                    String uidHeader = request.getHeader("X-User-Id");
                    if (StringUtils.hasText(uidHeader)) {
                        try {
                            request.setAttribute("p360.userId", Long.parseLong(uidHeader.trim()));
                        } catch (NumberFormatException ignored) {}
                    }
                    request.setAttribute("p360.email", email);
                    request.setAttribute("p360.role", rol.name());

                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7).trim();
        }
        return null;
    }

    private String getCookieValue(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (cookieName.equals(c.getName())) {
                    return c.getValue();
                }
            }
        }
        return null;
    }

    private boolean isMutatingMethod(String method) {
        return "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method);
    }

    private boolean requiresCsrfProtection(String path) {
        // Eximir endpoints públicos de autenticación inicial donde aún no hay sesión establecida
        return !path.equals("/api/auth/login") && !path.equals("/api/auth/register")
                && !path.equals("/api/auth/forgot-password") && !path.equals("/api/auth/bootstrap-admin");
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

    private Rol parseRol(String roleHeader) {
        for (Rol r : Rol.values()) {
            if (roleHeader.contains(r.name())) {
                return r;
            }
        }
        return Rol.CLIENTE;
    }

    private void sendErrorResponse(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(String.format("{\"success\":false,\"message\":\"%s\",\"data\":null}", message));
    }
}
