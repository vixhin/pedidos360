package cl.duoc.pedidos360.productos.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class StartupSecretsValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupSecretsValidator.class);

    private final Environment environment;

    @Value("${bff.internal-key:}")
    private String bffInternalKey;

    @Value("${spring.datasource.password:}")
    private String dbPassword;

    private static final List<String> INSECURE_VALUES = List.of(
            "postgres", "guest", "changeme", "admin", "password", "123456",
            "pedidos360-internal-secret-key-local-2026"
    );

    public StartupSecretsValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!isProduction()) {
            return;
        }

        log.info("[STARTUP-SECURITY][PRODUCTOS] Validando secretos para producción...");

        if (bffInternalKey == null || bffInternalKey.trim().length() < 32 || isInsecure(bffInternalKey)) {
            throw new IllegalStateException("FATAL: En PRODUCCIÓN, 'BFF_INTERNAL_KEY' debe estar definido, tener al menos 32 caracteres y no usar valores de desarrollo.");
        }

        if (dbPassword == null || dbPassword.isBlank() || isInsecure(dbPassword)) {
            throw new IllegalStateException("FATAL: En PRODUCCIÓN, 'DB_PASSWORD' debe estar definido y no usar valores por defecto inseguros.");
        }

        log.info("[STARTUP-SECURITY][PRODUCTOS] ✅ Secretos validados exitosamente.");
    }

    private boolean isInsecure(String val) {
        String lower = val.toLowerCase().trim();
        for (String bad : INSECURE_VALUES) {
            if (lower.equals(bad) || (bad.length() > 6 && lower.contains(bad))) {
                return true;
            }
        }
        return false;
    }

    private boolean isProduction() {
        if (environment == null) return false;
        return Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> "prod".equalsIgnoreCase(p) || "production".equalsIgnoreCase(p));
    }
}

