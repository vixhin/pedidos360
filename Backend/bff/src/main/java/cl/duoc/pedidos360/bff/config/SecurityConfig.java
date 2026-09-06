package cl.duoc.pedidos360.bff.config;

import cl.duoc.pedidos360.bff.exception.BffSecurityExceptionHandler;
import cl.duoc.pedidos360.bff.security.AudienceValidator;
import cl.duoc.pedidos360.bff.security.AzureJwtAuthConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;

/**
 * SecurityConfig — Configuración principal de seguridad del BFF.
 *
 * Política de seguridad:
 * - Sin sesión (STATELESS)
 * - Sin CSRF (API REST)
 * - OAuth2 Resource Server con validación JWT
 * - Autorización por roles (ROLE_xxx) y scopes (SCOPE_xxx)
 *
 * Validaciones automáticas de Spring Security:
 *   ✅ Firma RSA (via JWKS de Microsoft — automático)
 *   ✅ Issuer   (via issuer-uri — automático)
 *   ✅ Exp      (automático)
 *   ✅ Audience  (AudienceValidator — custom)
 *
 * Endpoints abiertos (sin JWT):
 *   GET /health
 *   GET /ready
 *   GET /actuator/health
 *
 * Endpoints protegidos (requieren JWT válido):
 *   /api/bff/**
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    private final AudienceValidator audienceValidator;
    private final AzureJwtAuthConverter jwtAuthConverter;
    private final BffSecurityExceptionHandler exceptionHandler;

    public SecurityConfig(AudienceValidator audienceValidator,
                          AzureJwtAuthConverter jwtAuthConverter,
                          BffSecurityExceptionHandler exceptionHandler) {
        this.audienceValidator = audienceValidator;
        this.jwtAuthConverter  = jwtAuthConverter;
        this.exceptionHandler  = exceptionHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // ── Sin estado (tokens, no sesiones) ──────────────────────────
            .sessionManagement(sm -> sm
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // ── Sin CSRF (API REST sin cookies de sesión) ─────────────────
            .csrf(csrf -> csrf.disable())

            // ── CORS (configurado en CorsConfig.java) ────────────────────
            .cors(cors -> {})

            // ── Respuestas de error en JSON ───────────────────────────────
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(exceptionHandler)
                .accessDeniedHandler(exceptionHandler))

            // ── Reglas de autorización ────────────────────────────────────
            .authorizeHttpRequests(authz -> authz

                // Endpoints de salud — sin token
                .requestMatchers("/health", "/ready", "/actuator/health", "/actuator/info")
                    .permitAll()

                // ── PRODUCTOS ─────────────────────────────────────────────
                // GET: cualquier usuario autenticado con scope
                .requestMatchers(HttpMethod.GET, "/api/bff/productos/**")
                    .hasAuthority("SCOPE_access_as_user")
                // POST/PUT: VENDEDOR o ADMIN
                .requestMatchers(HttpMethod.POST, "/api/bff/productos/**")
                    .hasAnyRole("VENDEDOR", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/bff/productos/**")
                    .hasAnyRole("VENDEDOR", "ADMIN")
                // DELETE: solo ADMIN
                .requestMatchers(HttpMethod.DELETE, "/api/bff/productos/**")
                    .hasRole("ADMIN")

                // ── PEDIDOS ───────────────────────────────────────────────
                .requestMatchers(HttpMethod.GET, "/api/bff/pedidos/**")
                    .hasAuthority("SCOPE_access_as_user")
                .requestMatchers(HttpMethod.POST, "/api/bff/pedidos/**")
                    .hasAuthority("SCOPE_access_as_user")
                // Actualización/eliminación: ADMIN
                .requestMatchers(HttpMethod.PUT, "/api/bff/pedidos/**")
                    .hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/bff/pedidos/**")
                    .hasRole("ADMIN")

                // ── CARRITO ───────────────────────────────────────────────
                .requestMatchers("/api/bff/carrito/**")
                    .hasAuthority("SCOPE_access_as_user")

                // ── NOTIFICACIONES ────────────────────────────────────────
                .requestMatchers("/api/bff/notificaciones/**")
                    .hasAuthority("SCOPE_access_as_user")

                // ── USUARIOS ──────────────────────────────────────────────
                // Perfil propio: autenticado
                .requestMatchers(HttpMethod.GET, "/api/bff/usuarios/me")
                    .hasAuthority("SCOPE_access_as_user")
                // Gestión: solo ADMIN
                .requestMatchers("/api/bff/usuarios/**")
                    .hasRole("ADMIN")

                // ── ANALÍTICA ─────────────────────────────────────────────
                .requestMatchers("/api/bff/analitica/**")
                    .hasRole("ADMIN")

                // Todo lo demás bajo /api/bff/** requiere autenticación
                .requestMatchers("/api/bff/**")
                    .authenticated()

                // Todo lo demás: permitido (health checks, actuator, etc.)
                .anyRequest().permitAll()
            )

            // ── OAuth2 Resource Server (JWT) ──────────────────────────────
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthConverter)));

        return http.build();
    }

    /**
     * JwtDecoder configurado con validadores adicionales:
     * 1. JwtTimestampValidator (exp, nbf) — automático
     * 2. JwtIssuerValidator   (iss)       — automático via issuerUri
     * 3. AudienceValidator    (aud)       — custom
     *
     * Spring descarga el JWKS desde Microsoft automáticamente.
     * NO se almacenan claves públicas manualmente.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        // NimbusJwtDecoder descarga las claves públicas desde:
        // https://login.microsoftonline.com/{tenantId}/v2.0/.well-known/openid-configuration
        NimbusJwtDecoder decoder = JwtDecoders.fromIssuerLocation(issuerUri);

        // Combinar validadores: los de Spring + el nuestro de audience
        OAuth2TokenValidator<Jwt> withIssuer    = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> withAudience  = new DelegatingOAuth2TokenValidator<>(
                withIssuer, audienceValidator);

        decoder.setJwtValidator(withAudience);
        return decoder;
    }
}
