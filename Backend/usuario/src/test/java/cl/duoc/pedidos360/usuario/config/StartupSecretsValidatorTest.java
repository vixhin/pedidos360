package cl.duoc.pedidos360.usuario.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StartupSecretsValidatorTest {

    @Test
    @DisplayName("StartupSecretsValidator - en DEV no lanza excepción aunque los secretos sean de desarrollo")
    void testDevProfilePermitted() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");

        StartupSecretsValidator validator = new StartupSecretsValidator(env);
        ReflectionTestUtils.setField(validator, "jwtSecret", "pedidos360_dev_secret_key_for_jwt_token_generation_local_environment_2026");
        ReflectionTestUtils.setField(validator, "bffInternalKey", "pedidos360-internal-secret-key-local-2026");
        ReflectionTestUtils.setField(validator, "dbPassword", "postgres");

        assertThatCode(() -> validator.run(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("StartupSecretsValidator - en PROD falla si JWT_SECRET contiene valor por defecto inseguro")
    void testProdFailsWithInsecureJwt() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        StartupSecretsValidator validator = new StartupSecretsValidator(env);
        ReflectionTestUtils.setField(validator, "jwtSecret", "pedidos360_dev_secret_key_for_jwt_token_generation_local_environment_2026");
        ReflectionTestUtils.setField(validator, "bffInternalKey", "real-secure-production-key-over-32-chars-long");
        ReflectionTestUtils.setField(validator, "dbPassword", "real-secure-db-password-12345");

        assertThatThrownBy(() -> validator.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    @DisplayName("StartupSecretsValidator - en PROD falla si Turnstile usa clave de prueba")
    void testProdFailsWithTestTurnstileKey() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        StartupSecretsValidator validator = new StartupSecretsValidator(env);
        ReflectionTestUtils.setField(validator, "jwtSecret", "super-secret-production-jwt-key-minimum-32-chars-ok");
        ReflectionTestUtils.setField(validator, "bffInternalKey", "super-secret-production-bff-key-minimum-32-chars-ok");
        ReflectionTestUtils.setField(validator, "dbPassword", "db-secret-secure-credential-999");
        ReflectionTestUtils.setField(validator, "turnstileEnabled", true);
        ReflectionTestUtils.setField(validator, "turnstileSecret", "1x0000000000000000000000000000000AA");

        assertThatThrownBy(() -> validator.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TURNSTILE_SECRET_KEY");
    }
}
