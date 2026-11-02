package cl.duoc.pedidos360.analitica.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StartupSecretsValidatorTest {

    @Test
    @DisplayName("En perfil dev no debe arrojar error aunque falten variables de prod")
    void testDevProfileSkipsValidation() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");

        StartupSecretsValidator validator = new StartupSecretsValidator(env);
        assertDoesNotThrow(validator::validate);
    }

    @Test
    @DisplayName("En perfil prod debe fallar si falta JWT_SECRET")
    void testProdProfileMissingJwtSecretThrows() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("spring.datasource.password", "secure-db-password-12345");
        env.setProperty("bff.internal-key", "a-very-long-internal-key-32-chars-long");

        StartupSecretsValidator validator = new StartupSecretsValidator(env);
        assertThrows(IllegalStateException.class, validator::validate);
    }

    @Test
    @DisplayName("En perfil prod con todas las variables seguras debe validar exitosamente")
    void testProdProfileSuccess() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("jwt.secret", "a-secure-jwt-secret-with-more-than-32-chars-length");
        env.setProperty("bff.internal-key", "a-very-long-internal-key-32-chars-long");
        env.setProperty("spring.datasource.password", "strong-unique-db-secret-password-xyz");

        StartupSecretsValidator validator = new StartupSecretsValidator(env);
        assertDoesNotThrow(validator::validate);
    }
}

