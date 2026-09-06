package cl.duoc.pedidos360.bff.security;

import cl.duoc.pedidos360.bff.BffApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

/**
 * BffSecurityTest — Tests de seguridad del BFF.
 *
 * Usa WebTestClient con mockJwt() de spring-security-test (reactivo).
 * NO requiere conexión a Azure.
 *
 * Escenarios cubiertos:
 * ✅ Sin token              → 401 Unauthorized + JSON
 * ✅ Token sin scope        → 403 Forbidden + JSON
 * ✅ Token CLIENTE sin ADMIN → 403 en /analitica
 * ✅ Token ADMIN            → pasa seguridad (200 o 5xx downstream)
 * ✅ Token VENDEDOR         → POST /productos pasa seguridad
 * ✅ Token CLIENTE          → GET /productos pasa seguridad
 * ✅ Token CLIENTE          → DELETE /productos = 403
 * ✅ /health                → 200 sin token
 * ✅ /ready                 → responde sin token
 */
@SpringBootTest(
    classes = BffApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@TestPropertySource(properties = {
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://login.microsoftonline.com/test-tenant/v2.0",
    "bff.azure.api-audience=api://test-api-client-id",
    "bff.frontend-origin=http://localhost:4200",
    "services.usuario-url=http://localhost:8081",
    "services.pedidos-url=http://localhost:8082",
    "services.carrito-url=http://localhost:8083",
    "services.analitica-url=http://localhost:8084",
    "services.productos-url=http://localhost:8085",
    "services.notificacion-url=http://localhost:8086"
})
class BffSecurityTest {

    @Autowired
    private WebTestClient webTestClient;

    // ─── HEALTH ENDPOINTS (sin token) ─────────────────────────────────────────

    @Test
    @DisplayName("/health debe responder 200 sin token")
    void health_sinToken_debe200() {
        webTestClient.get().uri("/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    @DisplayName("/ready debe responder sin token (no debe dar 401)")
    void ready_sinToken_noEsUnauthorized() {
        webTestClient.get().uri("/ready")
                .exchange()
                .expectStatus()
                .value(status -> {
                    assert status != 401 : "Expected no 401 for /ready without token";
                    assert status != 403 : "Expected no 403 for /ready without token";
                });
    }

    // ─── SIN TOKEN → 401 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Sin token → GET /api/bff/productos debe devolver 401 JSON")
    void sinToken_productos_debe401() {
        webTestClient.get().uri("/api/bff/productos")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.error").isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("Sin token → GET /api/bff/pedidos debe devolver 401")
    void sinToken_pedidos_debe401() {
        webTestClient.get().uri("/api/bff/pedidos")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error").isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("Sin token → GET /api/bff/analitica debe devolver 401")
    void sinToken_analitica_debe401() {
        webTestClient.get().uri("/api/bff/analitica")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.error").isEqualTo("UNAUTHORIZED");
    }

    // ─── TOKEN SIN SCOPE → 403 ────────────────────────────────────────────────

    @Test
    @DisplayName("Token sin scope 'access_as_user' → GET /productos debe 403 JSON")
    void tokenSinScope_debe403() {
        webTestClient.mutateWith(mockJwt()
                        .jwt(jwt -> jwt
                                .issuer("https://login.microsoftonline.com/test-tenant/v2.0")
                                .claim("aud", List.of("api://test-api-client-id"))
                                .claim("sub", "test-user")
                                // Sin scp → sin scope
                        ))
                .get().uri("/api/bff/productos")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.status").isEqualTo(403)
                .jsonPath("$.error").isEqualTo("FORBIDDEN");
    }

    // ─── TOKEN CLIENTE SIN ROL ADMIN → 403 en /analitica ─────────────────────

    @Test
    @DisplayName("Token CLIENTE → GET /analitica debe 403 (solo ADMIN)")
    void tokenCliente_analitica_debe403() {
        webTestClient.mutateWith(mockJwt()
                        .jwt(jwt -> jwt
                                .issuer("https://login.microsoftonline.com/test-tenant/v2.0")
                                .claim("aud", List.of("api://test-api-client-id"))
                                .claim("scp", "access_as_user")
                                .claim("roles", List.of("CLIENTE"))
                                .claim("sub", "test-cliente")
                        )
                        .authorities(
                                new SimpleGrantedAuthority("SCOPE_access_as_user"),
                                new SimpleGrantedAuthority("ROLE_CLIENTE")
                        ))
                .get().uri("/api/bff/analitica")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.error").isEqualTo("FORBIDDEN");
    }

    // ─── TOKEN ADMIN → pasa seguridad ─────────────────────────────────────────

    @Test
    @DisplayName("Token ADMIN → GET /analitica pasa seguridad (no 401/403)")
    void tokenAdmin_analitica_pasaSeguridad() {
        webTestClient.mutateWith(mockJwt()
                        .jwt(jwt -> jwt
                                .issuer("https://login.microsoftonline.com/test-tenant/v2.0")
                                .claim("aud", List.of("api://test-api-client-id"))
                                .claim("scp", "access_as_user")
                                .claim("roles", List.of("ADMIN"))
                                .claim("sub", "test-admin")
                        )
                        .authorities(
                                new SimpleGrantedAuthority("SCOPE_access_as_user"),
                                new SimpleGrantedAuthority("ROLE_ADMIN")
                        ))
                .get().uri("/api/bff/analitica")
                .exchange()
                .expectStatus()
                .value(status -> {
                    assert status != 401 : "No debe ser 401";
                    assert status != 403 : "No debe ser 403";
                });
    }

    // ─── TOKEN VENDEDOR → POST /productos ─────────────────────────────────────

    @Test
    @DisplayName("Token VENDEDOR → POST /productos pasa seguridad")
    void tokenVendedor_postProductos_pasaSeguridad() {
        webTestClient.mutateWith(mockJwt()
                        .jwt(jwt -> jwt
                                .issuer("https://login.microsoftonline.com/test-tenant/v2.0")
                                .claim("aud", List.of("api://test-api-client-id"))
                                .claim("scp", "access_as_user")
                                .claim("roles", List.of("VENDEDOR"))
                                .claim("sub", "test-vendedor")
                        )
                        .authorities(
                                new SimpleGrantedAuthority("SCOPE_access_as_user"),
                                new SimpleGrantedAuthority("ROLE_VENDEDOR")
                        ))
                .post().uri("/api/bff/productos")
                .bodyValue("{\"nombre\":\"test\"}")
                .header("Content-Type", "application/json")
                .exchange()
                .expectStatus()
                .value(status -> {
                    assert status != 401 : "No debe ser 401";
                    assert status != 403 : "No debe ser 403";
                });
    }

    // ─── TOKEN CLIENTE → GET /productos ──────────────────────────────────────

    @Test
    @DisplayName("Token CLIENTE con scope → GET /productos pasa seguridad")
    void tokenCliente_getProductos_pasaSeguridad() {
        webTestClient.mutateWith(mockJwt()
                        .jwt(jwt -> jwt
                                .issuer("https://login.microsoftonline.com/test-tenant/v2.0")
                                .claim("aud", List.of("api://test-api-client-id"))
                                .claim("scp", "access_as_user")
                                .claim("roles", List.of("CLIENTE"))
                                .claim("sub", "test-cliente")
                        )
                        .authorities(
                                new SimpleGrantedAuthority("SCOPE_access_as_user"),
                                new SimpleGrantedAuthority("ROLE_CLIENTE")
                        ))
                .get().uri("/api/bff/productos")
                .exchange()
                .expectStatus()
                .value(status -> {
                    assert status != 401 : "No debe ser 401";
                    assert status != 403 : "No debe ser 403";
                });
    }

    // ─── TOKEN CLIENTE → DELETE /productos → 403 ─────────────────────────────

    @Test
    @DisplayName("Token CLIENTE → DELETE /productos debe 403 (solo ADMIN)")
    void tokenCliente_deleteProductos_debe403() {
        webTestClient.mutateWith(mockJwt()
                        .jwt(jwt -> jwt
                                .issuer("https://login.microsoftonline.com/test-tenant/v2.0")
                                .claim("aud", List.of("api://test-api-client-id"))
                                .claim("scp", "access_as_user")
                                .claim("roles", List.of("CLIENTE"))
                                .claim("sub", "test-cliente")
                        )
                        .authorities(
                                new SimpleGrantedAuthority("SCOPE_access_as_user"),
                                new SimpleGrantedAuthority("ROLE_CLIENTE")
                        ))
                .delete().uri("/api/bff/productos/1")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.error").isEqualTo("FORBIDDEN");
    }
}
