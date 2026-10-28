package cl.duoc.pedidos360.bff.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProxyControllerTest {

    @Mock
    private WebClient productosClient;
    @Mock
    private WebClient pedidosClient;
    @Mock
    private WebClient carritoClient;
    @Mock
    private WebClient notificacionClient;
    @Mock
    private WebClient analiticaClient;
    @Mock
    private WebClient usuarioClient;
    @Mock
    private WebClient chatClient;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;
    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    private ProxyController proxyController;

    @BeforeEach
    void setUp() {
        proxyController = new ProxyController(
                productosClient,
                pedidosClient,
                carritoClient,
                notificacionClient,
                analiticaClient,
                usuarioClient,
                chatClient
        );
    }

    private Jwt createMockJwt() {
        return Jwt.withTokenValue("mock-token")
                .header("alg", "RS256")
                .claim("sub", "user-123")
                .claim("email", "test@pedidos360.cl")
                .claim("roles", List.of("CLIENTE"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }

    private void setupMockWebClient(ClientResponse clientResponse) {
        when(productosClient.method(any(HttpMethod.class))).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);

        when(requestBodySpec.exchangeToMono(any())).thenAnswer(invocation -> {
            Function<ClientResponse, Mono<ResponseEntity<String>>> fn = invocation.getArgument(0);
            return fn.apply(clientResponse);
        });
    }

    @Test
    @DisplayName("1. GET downstream 200 OK -> BFF preserva 200 OK con body y Content-Type")
    void testGetDownstream200Preserved() {
        ClientResponse downstreamResponse = ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("{\"message\":\"catalogo\"}")
                .build();
        setupMockWebClient(downstreamResponse);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/bff/productos");

        ResponseEntity<String> response = proxyController.proxiarProductos(request, createMockJwt(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("{\"message\":\"catalogo\"}");
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
    }

    @Test
    @DisplayName("2. POST downstream 201 CREATED -> BFF preserva 201 CREATED")
    void testPostDownstream201Preserved() {
        ClientResponse downstreamResponse = ClientResponse.create(HttpStatus.CREATED)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("{\"id\":10,\"nombre\":\"Nuevo Producto\"}")
                .build();
        setupMockWebClient(downstreamResponse);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/bff/productos");

        ResponseEntity<String> response = proxyController.proxiarProductos(request, createMockJwt(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo("{\"id\":10,\"nombre\":\"Nuevo Producto\"}");
    }

    @Test
    @DisplayName("3. DELETE downstream 204 NO CONTENT -> BFF preserva 204 sin body")
    void testDeleteDownstream204Preserved() {
        ClientResponse downstreamResponse = ClientResponse.create(HttpStatus.NO_CONTENT).build();
        setupMockWebClient(downstreamResponse);

        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/bff/productos/10");

        ResponseEntity<String> response = proxyController.proxiarProductos(request, createMockJwt(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
    }

    @Test
    @DisplayName("4. Downstream 400 BAD REQUEST -> BFF preserva 400 y error body")
    void testDownstream400Preserved() {
        ClientResponse downstreamResponse = ClientResponse.create(HttpStatus.BAD_REQUEST)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("{\"error\":\"Datos inválidos\"}")
                .build();
        setupMockWebClient(downstreamResponse);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/bff/productos");

        ResponseEntity<String> response = proxyController.proxiarProductos(request, createMockJwt(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo("{\"error\":\"Datos inválidos\"}");
    }

    @Test
    @DisplayName("5. Downstream 404 NOT FOUND -> BFF preserva 404 y error body")
    void testDownstream404Preserved() {
        ClientResponse downstreamResponse = ClientResponse.create(HttpStatus.NOT_FOUND)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("{\"error\":\"Producto no encontrado\"}")
                .build();
        setupMockWebClient(downstreamResponse);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/bff/productos/999");

        ResponseEntity<String> response = proxyController.proxiarProductos(request, createMockJwt(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isEqualTo("{\"error\":\"Producto no encontrado\"}");
    }

    @Test
    @DisplayName("6. Downstream 500 INTERNAL SERVER ERROR -> BFF preserva 500 y error body")
    void testDownstream500Preserved() {
        ClientResponse downstreamResponse = ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body("{\"error\":\"Fallo interno downstream\"}")
                .build();
        setupMockWebClient(downstreamResponse);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/bff/productos/error");

        ResponseEntity<String> response = proxyController.proxiarProductos(request, createMockJwt(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isEqualTo("{\"error\":\"Fallo interno downstream\"}");
    }

    @Test
    @DisplayName("7. Downstream inalcanzable (error de red) -> BFF retorna 503 SERVICE UNAVAILABLE")
    void testServiceUnavailableThrows503() {
        when(productosClient.method(any(HttpMethod.class))).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.header(anyString(), anyString())).thenReturn(requestBodySpec);

        when(requestBodySpec.exchangeToMono(any())).thenAnswer(invocation ->
                Mono.error(new java.net.ConnectException("Connection refused"))
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/bff/productos");

        assertThatThrownBy(() -> proxyController.proxiarProductos(request, createMockJwt(), null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                });
    }
}

