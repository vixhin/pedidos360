package cl.duoc.pedidos360.bff.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AudienceValidatorTest {

    @Test
    @DisplayName("validate - éxito cuando contiene el audience esperado")
    void testValidateExito() {
        AudienceValidator validator = new AudienceValidator();
        // Set expectedAudience field
        org.springframework.test.util.ReflectionTestUtils.setField(validator, "expectedAudience", "api://test-client-id");

        org.springframework.security.oauth2.jwt.Jwt jwt = new org.springframework.security.oauth2.jwt.Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "RS256"),
                Map.of("aud", List.of("api://test-client-id"))
        );

        org.springframework.security.oauth2.core.OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("validate - error cuando audience no coincide")
    void testValidateInvalido() {
        AudienceValidator validator = new AudienceValidator();
        org.springframework.test.util.ReflectionTestUtils.setField(validator, "expectedAudience", "api://expected-id");

        org.springframework.security.oauth2.jwt.Jwt jwt = new org.springframework.security.oauth2.jwt.Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "RS256"),
                Map.of("aud", List.of("api://wrong-id"))
        );

        org.springframework.security.oauth2.core.OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertThat(result.hasErrors()).isTrue();
    }
}
