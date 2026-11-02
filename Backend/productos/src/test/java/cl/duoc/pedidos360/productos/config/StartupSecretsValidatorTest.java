package cl.duoc.pedidos360.productos.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StartupSecretsValidatorTest {

    @Test
    @DisplayName("StartupSecretsValidator - en DEV no lanza excepción")
    void testDevProfilePermitted() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");

        StartupSecretsValidator validator = new StartupSecretsValidator(env);
        ReflectionTestUtils.setField(validator, "bffInternalKey", "pedidos360-internal-secret-key-local-2026");
        ReflectionTestUtils.setField(validator, "dbPassword", "postgres");

        assertThatCode(() -> validator.run(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("StartupSecretsValidator - en PROD falla si falta BFF_INTERNAL_KEY o es inseguro")
    void testProdFailsWithInsecureBffKey() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        StartupSecretsValidator validator = new StartupSecretsValidator(env);
        ReflectionTestUtils.setField(validator, "bffInternalKey", "pedidos360-internal-secret-key-local-2026");
        ReflectionTestUtils.setField(validator, "dbPassword", "real-prod-db-password-12345");

        assertThatThrownBy(() -> validator.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BFF_INTERNAL_KEY");
    }
}

