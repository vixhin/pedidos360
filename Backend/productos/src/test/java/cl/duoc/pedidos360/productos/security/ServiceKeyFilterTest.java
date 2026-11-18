package cl.duoc.pedidos360.productos.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/** Pruebas de seguridad del control de acceso de productos-service. */
class ServiceKeyFilterTest {

    private static final String SECRET = "test-jwt-secret-key-with-at-least-32-characters";
    private static final String INTERNAL_KEY = "test-internal-secret-key-32chars-long";

    private ServiceKeyFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ServiceKeyFilter(new JwtUtil(SECRET));
        ReflectionTestUtils.setField(filter, "bffInternalKey", INTERNAL_KEY);
        ReflectionTestUtils.setField(filter, "autoDevAdmin", false);
    }

    private String token(long uid, String role) {
        return Jwts.builder()
                .subject("user" + uid + "@test.cl")
                .claim("uid", uid)
                .claim("role", role)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    /** Ejecuta el filtro y devuelve el status (200 si dejó pasar la petición). */
    private int ejecutar(String method, String path, String bearer, String internalKey) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(method, path);
        if (bearer != null) {
            req.addHeader("Authorization", "Bearer " + bearer);
        }
        if (internalKey != null) {
            req.addHeader("X-Internal-Service-Key", internalKey);
        }
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(req, res, chain);
        return chain.getRequest() != null ? 200 : res.getStatus();
    }

    @Test
    @DisplayName("catálogo (GET) es público")
    void catalogoPublico() throws Exception {
        assertThat(ejecutar("GET", "/api/productos", null, null)).isEqualTo(200);
        assertThat(ejecutar("GET", "/api/productos/1", null, null)).isEqualTo(200);
    }

    @Test
    @DisplayName("anónimo no puede cambiar el precio de un producto -> 401")
    void anonimoNoEditaProducto() throws Exception {
        assertThat(ejecutar("PUT", "/api/productos/1", null, null)).isEqualTo(401);
        assertThat(ejecutar("DELETE", "/api/productos/1", null, null)).isEqualTo(401);
        assertThat(ejecutar("POST", "/api/productos", null, null)).isEqualTo(401);
    }

    @Test
    @DisplayName("cliente no puede editar productos -> 403; vendedor y admin sí")
    void soloGestoresEditan() throws Exception {
        assertThat(ejecutar("PUT", "/api/productos/1", token(10, "CLIENTE"), null)).isEqualTo(403);
        assertThat(ejecutar("PUT", "/api/productos/1", token(2, "VENDEDOR"), null)).isEqualTo(200);
        assertThat(ejecutar("PUT", "/api/productos/1", token(1, "ADMIN"), null)).isEqualTo(200);
    }

    @Test
    @DisplayName("descontar stock sólo con clave interna (ni siquiera un admin desde el navegador)")
    void descontarStockSoloInterno() throws Exception {
        String path = "/api/productos/1/stock/descontar";
        assertThat(ejecutar("PUT", path, null, null)).isEqualTo(401);
        assertThat(ejecutar("PUT", path, token(1, "ADMIN"), null)).isEqualTo(403);
        assertThat(ejecutar("PUT", path, null, "clave-incorrecta")).isEqualTo(401);
        assertThat(ejecutar("PUT", path, null, INTERNAL_KEY)).isEqualTo(200);
    }

    @Test
    @DisplayName("importar catálogo semilla sólo para ADMIN")
    void importarSoloAdmin() throws Exception {
        String path = "/api/productos/dev/importar-catalogo-semilla";
        assertThat(ejecutar("POST", path, token(2, "VENDEDOR"), null)).isEqualTo(403);
        assertThat(ejecutar("POST", path, token(1, "ADMIN"), null)).isEqualTo(200);
    }

    @Test
    @DisplayName("favoritos: sólo el dueño (IDOR)")
    void favoritosSoloDueno() throws Exception {
        assertThat(ejecutar("GET", "/api/productos/favoritos/10", null, null)).isEqualTo(401);
        assertThat(ejecutar("GET", "/api/productos/favoritos/20", token(10, "CLIENTE"), null)).isEqualTo(403);
        assertThat(ejecutar("DELETE", "/api/productos/favoritos/20/5", token(10, "CLIENTE"), null)).isEqualTo(403);
        assertThat(ejecutar("GET", "/api/productos/favoritos/10", token(10, "CLIENTE"), null)).isEqualTo(200);
    }

    @Test
    @DisplayName("token con firma inválida -> 401")
    void tokenFalsificado() throws Exception {
        String falso = Jwts.builder().subject("x").claim("uid", 1).claim("role", "ADMIN")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor("otra-clave-secreta-de-al-menos-32-caracteres!!".getBytes(StandardCharsets.UTF_8)))
                .compact();
        assertThat(ejecutar("PUT", "/api/productos/1", falso, null)).isEqualTo(401);
    }
}
