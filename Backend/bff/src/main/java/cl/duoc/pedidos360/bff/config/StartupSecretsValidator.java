package cl.duoc.pedidos360.bff.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class StartupSecretsValidator {

    private static final Logger log = LoggerFactory.getLogger(StartupSecretsValidator.class);

    private static final List<String> KNOWN_INSECURE_FRAGMENTS = List.of(
            "changeme", "changeit", "default", "example",
            "test-secret", "dev_secret", "dummy"
    );

    private final Environment env;

    @Value("${spring.profiles.active:dev}")
    private String activeProfiles = "dev";

    public StartupSecretsValidator(Environment env) {
        this.env = env;
    }

    public void setActiveProfiles(String activeProfiles) {
        this.activeProfiles = activeProfiles;
    }

    @PostConstruct
    public void validate() {
        boolean isProd = Arrays.stream(env.getActiveProfiles())
                .anyMatch(p -> p.equalsIgnoreCase("prod") || p.equalsIgnoreCase("production"));

        if (!isProd && (activeProfiles == null || !activeProfiles.toLowerCase().contains("prod"))) {
            log.info("[STARTUP-SECRETS] Perfil no-prod activo ('{}'). Validación estricta de secretos omitida.", activeProfiles);
            return;
        }

        log.info("[STARTUP-SECRETS] Ejecutando validación estricta de secretos para bff-service en entorno PRODUCCIÓN...");

        validateSecret("BFF_INTERNAL_KEY", env.getProperty("bff.internal-key"), 32);

        log.info("[STARTUP-SECRETS] Todos los secretos de producción para bff-service fueron validados exitosamente.");
    }

    private void validateSecret(String name, String value, int minLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(String.format(
                    "FATAL: La variable de entorno / propiedad '%s' es obligatoria en producción y no fue provista.", name
            ));
        }
        if (value.length() < minLength) {
            throw new IllegalStateException(String.format(
                    "FATAL: La variable de entorno / propiedad '%s' debe tener al menos %d caracteres de entropía (longitud actual: %d).",
                    name, minLength, value.length()
            ));
        }
        if (isInsecure(value)) {
            throw new IllegalStateException(String.format(
                    "FATAL: '%s' contiene un valor de ejemplo o inseguro conocido.", name
            ));
        }
    }

    private boolean isInsecure(String val) {
        String lower = val.toLowerCase();
        return KNOWN_INSECURE_FRAGMENTS.stream().anyMatch(lower::contains);
    }
}

