package cl.duoc.pedidos360.bff.config;

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
    @DisplayName("En perfil prod debe fallar si falta BFF_INTERNAL_KEY")
    void testProdProfileMissingInternalKeyThrows() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        StartupSecretsValidator validator = new StartupSecretsValidator(env);
        assertThrows(IllegalStateException.class, validator::validate);
    }

    @Test
    @DisplayName("En perfil prod con clave segura debe validar exitosamente")
    void testProdProfileSuccess() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("bff.internal-key", "a-very-long-internal-key-32-chars-long");

        StartupSecretsValidator validator = new StartupSecretsValidator(env);
        assertDoesNotThrow(validator::validate);
    }
}

