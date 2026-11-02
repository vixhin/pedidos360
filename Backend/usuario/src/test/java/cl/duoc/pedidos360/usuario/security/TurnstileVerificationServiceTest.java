package cl.duoc.pedidos360.usuario.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TurnstileVerificationServiceTest {

    @Test
    @DisplayName("verifyToken - token nulo o vacío debe ser rechazado inmediatamente")
    void testTokenVacioRechazado() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        TurnstileVerificationService turnstileService = new TurnstileVerificationService(new RestTemplate(), env);
        turnstileService.setTurnstileEnabled(true);

        assertThat(turnstileService.verifyToken(null, "127.0.0.1")).isFalse();
        assertThat(turnstileService.verifyToken("   ", "127.0.0.1")).isFalse();
    }

    @Test
    @DisplayName("verifyToken - respuesta exitosa de Cloudflare retorna true")
    void testTokenValidoExitoso() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");

        RestTemplate mockRest = new RestTemplate() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> ResponseEntity<T> postForEntity(String url, Object request, Class<T> responseType, Object... uriVariables) {
                Map<String, Object> body = new HashMap<>();
                body.put("success", true);
                body.put("hostname", "example.com");
                return new ResponseEntity<>((T) body, HttpStatus.OK);
            }
        };

        TurnstileVerificationService turnstileService = new TurnstileVerificationService(mockRest, env);
        turnstileService.setTurnstileEnabled(true);
        boolean result = turnstileService.verifyToken("valid-token-123", "127.0.0.1", null);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("verifyToken - Cloudflare reporta error success=false retorna false")
    void testTokenRechazadoPorCloudflare() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");

        RestTemplate mockRest = new RestTemplate() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> ResponseEntity<T> postForEntity(String url, Object request, Class<T> responseType, Object... uriVariables) {
                Map<String, Object> body = new HashMap<>();
                body.put("success", false);
                body.put("error-codes", List.of("invalid-input-response"));
                return new ResponseEntity<>((T) body, HttpStatus.OK);
            }
        };

        TurnstileVerificationService turnstileService = new TurnstileVerificationService(mockRest, env);
        turnstileService.setTurnstileEnabled(true);
        boolean result = turnstileService.verifyToken("invalid-token", "127.0.0.1", null);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("verifyToken - timeout o falla de red aplica política fail-closed (retorna false)")
    void testFallaDeRedFailClosed() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");

        RestTemplate mockRest = new RestTemplate() {
            @Override
            public <T> ResponseEntity<T> postForEntity(String url, Object request, Class<T> responseType, Object... uriVariables) {
                throw new ResourceAccessException("Connection timed out");
            }
        };

        TurnstileVerificationService turnstileService = new TurnstileVerificationService(mockRest, env);
        turnstileService.setTurnstileEnabled(true);
        boolean result = turnstileService.verifyToken("any-token", "127.0.0.1", null);
        assertThat(result).isFalse();
    }
}
