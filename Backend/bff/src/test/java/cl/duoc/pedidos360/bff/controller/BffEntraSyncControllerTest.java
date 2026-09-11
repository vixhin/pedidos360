package cl.duoc.pedidos360.bff.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BffEntraSyncControllerTest {

    @Mock
    private WebClient usuarioClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    private BffEntraSyncController controller;
    private final String internalKey = "test-internal-secret-key";

    @BeforeEach
    void setUp() {
        controller = new BffEntraSyncController(usuarioClient, internalKey);
    }

    @Test
    @DisplayName("1. entra-sync sin token -> 401 Unauthorized")
    void testSyncSinTokenReturns401() {
        ResponseEntity<?> response = controller.syncFromJwt(null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("2. entra-sync con token válido -> 200 OK y propaga a usuario-service")
    void testSyncConTokenValido() {
        Jwt jwt = createMockJwt("user@pedidos360.cl", "Usuario Test", List.of("CLIENTE"));

        setupMockWebClient("{\"success\":true,\"data\":{\"id\":1,\"email\":\"user@pedidos360.cl\",\"rol\":\"CLIENTE\"}}");

        ResponseEntity<?> response = controller.syncFromJwt(jwt);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("3. entra-sync obtiene email desde preferred_username / upn / email")
    void testSyncObtieneEmailDesdeClaims() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", "sub-123")
                .claim("preferred_username", "preferred@pedidos360.cl")
                .claim("name", "Preferred User")
                .claim("roles", List.of("CLIENTE"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        setupMockWebClient("{\"success\":true}");

        controller.syncFromJwt(jwt);

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(requestBodySpec).bodyValue(captor.capture());
        Map<String, String> payload = captor.getValue();
        assertThat(payload.get("email")).isEqualTo("preferred@pedidos360.cl");
    }

    @Test
    @DisplayName("4, 8. token con rol ADMIN -> asigna ADMIN")
    void testSyncTokenAdmin() {
        Jwt jwt = createMockJwt("admin@pedidos360.cl", "Admin User", List.of("ADMIN"));

        setupMockWebClient("{\"success\":true}");

        controller.syncFromJwt(jwt);

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(requestBodySpec).bodyValue(captor.capture());
        assertThat(captor.getValue().get("rol")).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("9. token con rol VENDEDOR -> asigna VENDEDOR")
    void testSyncTokenVendedor() {
        Jwt jwt = createMockJwt("seller@pedidos360.cl", "Seller User", List.of("VENDEDOR"));

        setupMockWebClient("{\"success\":true}");

        controller.syncFromJwt(jwt);

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(requestBodySpec).bodyValue(captor.capture());
        assertThat(captor.getValue().get("rol")).isEqualTo("VENDEDOR");
    }

    @Test
    @DisplayName("7. token sin roles -> asigna CLIENTE por defecto")
    void testSyncTokenSinRoles() {
        Jwt jwt = createMockJwt("user@pedidos360.cl", "Normal User", List.of());

        setupMockWebClient("{\"success\":true}");

        controller.syncFromJwt(jwt);

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(requestBodySpec).bodyValue(captor.capture());
        assertThat(captor.getValue().get("rol")).isEqualTo("CLIENTE");
    }

    @Test
    @DisplayName("5, 6. CLIENTE no puede escalar a ADMIN; no acepta rol enviado manualmente desde frontend")
    void testClienteNoEscalaAdmin() {
        // En BffEntraSyncController no hay parámetro de request body que acepte 'rol',
        // la función syncFromJwt(Jwt jwt) ignora cualquier payload del frontend
        // y toma la información únicamente del Jwt.
        Jwt jwt = createMockJwt("fakeadmin@pedidos360.cl", "Fake Admin", List.of("CLIENTE"));

        setupMockWebClient("{\"success\":true}");

        controller.syncFromJwt(jwt);

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(requestBodySpec).bodyValue(captor.capture());
        // El rol resuelto DEBE ser CLIENTE porque el token solo contenía CLIENTE
        assertThat(captor.getValue().get("rol")).isEqualTo("CLIENTE");
    }

    @SuppressWarnings("unchecked")
    private void setupMockWebClient(String responseJson) {
        when(usuarioClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just(responseJson));
    }

    private Jwt createMockJwt(String email, String name, List<String> roles) {
        return Jwt.withTokenValue("mock-jwt-token")
                .header("alg", "RS256")
                .claim("sub", "user-oid-12345")
                .claim("preferred_username", email)
                .claim("name", name)
                .claim("roles", roles)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
}
