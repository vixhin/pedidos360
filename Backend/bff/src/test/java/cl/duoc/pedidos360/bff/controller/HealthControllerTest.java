package cl.duoc.pedidos360.bff.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HealthControllerTest {

    @Mock
    private WebClient productosClient;

    @Mock
    private WebClient pedidosClient;

    private HealthController healthController;

    @BeforeEach
    void setUp() {
        productosClient = mock(WebClient.class, RETURNS_DEEP_STUBS);
        pedidosClient = mock(WebClient.class, RETURNS_DEEP_STUBS);
        healthController = new HealthController(productosClient, pedidosClient);
    }

    @Test
    @DisplayName("health - retorna estado UP")
    void testHealth() {
        ResponseEntity<Map<String, Object>> response = healthController.health();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status")).isEqualTo("UP");
        assertThat(response.getBody().get("service")).isEqualTo("bff");
    }

    @Test
    @DisplayName("ready - retorna READY si al menos un servicio responde")
    void testReadyExito() {
        ResponseEntity<Map<String, Object>> response = healthController.ready();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("status")).isEqualTo("READY");
    }

    @Test
    @DisplayName("ready - retorna NOT_READY si todos los servicios fallan")
    void testReadyFallido() {
        when(productosClient.get()).thenThrow(new RuntimeException("Downstream error"));
        when(pedidosClient.get()).thenThrow(new RuntimeException("Downstream error"));

        ResponseEntity<Map<String, Object>> response = healthController.ready();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().get("status")).isEqualTo("NOT_READY");
    }
}
