package cl.duoc.pedidos360.usuario.config;

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

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${bff.internal-key:}")
    private String bffInternalKey;

    @Value("${spring.datasource.password:}")
    private String dbPassword;

    @Value("${turnstile.secret-key:}")
    private String turnstileSecret;

    @Value("${turnstile.enabled:true}")
    private boolean turnstileEnabled;

    private static final List<String> INSECURE_VALUES = List.of(
            "postgres", "guest", "changeme", "admin", "password", "123456",
            "pedidos360-internal-secret-key-local-2026",
            "pedidos360_dev_secret_key_for_jwt_token_generation_local_environment_2026"
    );

    public StartupSecretsValidator(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!isProduction()) {
            log.info("[STARTUP-SECURITY] Perfil no productivo detectado. Validación estricta de secretos omitida para DEV.");
            return;
        }

        log.info("[STARTUP-SECURITY] Iniciando validación estricta de secretos para entorno PRODUCCIÓN...");

        // 1. JWT Secret
        if (jwtSecret == null || jwtSecret.trim().length() < 32 || isInsecure(jwtSecret)) {
            throw new IllegalStateException("FATAL: En PRODUCCIÓN, 'JWT_SECRET' debe estar definido, tener al menos 32 caracteres y no usar valores de desarrollo.");
        }

        // 2. BFF Internal Key
        if (bffInternalKey == null || bffInternalKey.trim().length() < 32 || isInsecure(bffInternalKey)) {
            throw new IllegalStateException("FATAL: En PRODUCCIÓN, 'BFF_INTERNAL_KEY' debe estar definido, tener al menos 32 caracteres y no usar valores de desarrollo.");
        }

        // 3. Database Password
        if (dbPassword == null || dbPassword.isBlank() || isInsecure(dbPassword)) {
            throw new IllegalStateException("FATAL: En PRODUCCIÓN, 'DB_PASSWORD' debe estar definido y no usar valores por defecto inseguros.");
        }

        // 4. Cloudflare Turnstile
        if (!turnstileEnabled) {
            throw new IllegalStateException("FATAL: En PRODUCCIÓN, 'TURNSTILE_ENABLED' debe ser true obligatoriamente.");
        }
        if (turnstileSecret == null || turnstileSecret.isBlank() ||
                turnstileSecret.startsWith("1x0000000000000000000000000000000AA") ||
                turnstileSecret.startsWith("2x0000000000000000000000000000000AA") ||
                turnstileSecret.startsWith("3x0000000000000000000000000000000AA")) {
            throw new IllegalStateException("FATAL: En PRODUCCIÓN, 'TURNSTILE_SECRET_KEY' no puede ser una clave de prueba de Cloudflare.");
        }

        log.info("[STARTUP-SECURITY] ✅ Todos los secretos de PRODUCCIÓN fueron validados exitosamente.");
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

