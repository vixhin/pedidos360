package cl.duoc.pedidos360.usuario.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @org.springframework.beans.factory.annotation.Value("${bff.internal-key:${BFF_INTERNAL_KEY:}}")
    private String internalKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = parseJwt(request);

        if (token != null && jwtUtil.validateToken(token)) {
            String email = jwtUtil.extractEmail(token);
            String role = jwtUtil.extractRole(token);

            SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(email, null, Collections.singletonList(authority));

            SecurityContextHolder.getContext().setAuthentication(authentication);
        } else {
            // Soporte para llamadas autenticadas reenviadas desde BFF vía internal-key y headers de rol
            String clientKey = request.getHeader("X-Internal-Service-Key");
            if (StringUtils.hasText(internalKey) && internalKey.equals(clientKey)) {
                String email = request.getHeader("X-User-Email");
                if (!StringUtils.hasText(email)) {
                    email = request.getHeader("X-User-Sub");
                }
                String rolesHeader = request.getHeader("X-User-Roles");
                java.util.List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
                if (StringUtils.hasText(rolesHeader)) {
                    if (rolesHeader.contains("ADMIN") || rolesHeader.contains("ROLE_ADMIN")) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                    }
                    if (rolesHeader.contains("CLIENTE") || rolesHeader.contains("ROLE_CLIENTE")) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_CLIENTE"));
                    }
                    if (rolesHeader.contains("REPARTIDOR") || rolesHeader.contains("ROLE_REPARTIDOR")) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_REPARTIDOR"));
                    }
                    if (rolesHeader.contains("VENDEDOR") || rolesHeader.contains("ROLE_VENDEDOR")) {
                        authorities.add(new SimpleGrantedAuthority("ROLE_VENDEDOR"));
                    }
                }
                if (!authorities.isEmpty()) {
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(email != null ? email : "internal-admin", null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }
        return null;
    }
}
