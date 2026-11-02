package cl.duoc.pedidos360.usuario.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class TurnstileVerificationService {

    private static final Logger log = LoggerFactory.getLogger(TurnstileVerificationService.class);
    private static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";
    public static final String CLOUDFLARE_TEST_SECRET_PASS = "1x0000000000000000000000000000000AA";

    private final RestTemplate restTemplate;
    private final Environment environment;

    @Value("${turnstile.secret-key:${TURNSTILE_SECRET_KEY:1x0000000000000000000000000000000AA}}")
    private String secretKey;

    @Value("${turnstile.enabled:${TURNSTILE_ENABLED:true}}")
    private boolean turnstileEnabled = true;

    public void setTurnstileEnabled(boolean turnstileEnabled) {
        this.turnstileEnabled = turnstileEnabled;
    }

    @Value("${turnstile.allowed-hostnames:${TURNSTILE_ALLOWED_HOSTNAMES:localhost,127.0.0.1,example.com}}")
    private String allowedHostnamesStr;

    @Value("${turnstile.verify-action:false}")
    private boolean verifyAction;

    public TurnstileVerificationService(@Autowired(required = false) RestTemplate restTemplate, Environment environment) {
        if (restTemplate != null) {
            this.restTemplate = restTemplate;
        } else {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(4000);
            factory.setReadTimeout(4000);
            this.restTemplate = new RestTemplate(factory);
        }
        this.environment = environment;
    }

    public boolean isEnabled() {
        return turnstileEnabled;
    }

    public boolean verifyToken(String token, String remoteIp) {
        return verifyToken(token, remoteIp, null);
    }

    public boolean verifyToken(String token, String remoteIp, String expectedAction) {
        boolean isProd = isProductionProfile();

        if (!turnstileEnabled) {
            if (isProd) {
                log.error("[TURNSTILE][CRITICAL] Intento de bypass de Turnstile en entorno de PRODUCCIÓN. Rechazado.");
                return false;
            }
            log.info("[TURNSTILE] Verificación desactivada por configuración (entorno no-prod).");
            return true;
        }

        if (token == null || token.trim().isEmpty() || token.length() > 2048) {
            log.warn("[TURNSTILE] Verificación rechazada: token nulo, vacío o supera tamaño máximo.");
            return false;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("secret", secretKey != null ? secretKey.trim() : "");
            body.add("response", token.trim());
            if (remoteIp != null && !remoteIp.isBlank()) {
                body.add("remoteip", remoteIp.trim());
            }

            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(VERIFY_URL, requestEntity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                Object successVal = responseBody.get("success");
                boolean success = Boolean.TRUE.equals(successVal);

                if (!success) {
                    Object errorCodes = responseBody.get("error-codes");
                    log.warn("[TURNSTILE] Cloudflare rechazó token con error: {}", errorCodes);
                    return false;
                }

                // Validación de Hostname
                String returnedHostname = (String) responseBody.get("hostname");
                if (returnedHostname != null && !isHostnameAllowed(returnedHostname, isProd)) {
                    log.warn("[TURNSTILE] Hostname no permitido: '{}'", returnedHostname);
                    return false;
                }

                // Validación opcional de Action
                if (verifyAction && expectedAction != null) {
                    String returnedAction = (String) responseBody.get("action");
                    if (returnedAction != null && !expectedAction.equalsIgnoreCase(returnedAction)) {
                        log.warn("[TURNSTILE] Acción no coincide. Esperada: '{}', recibida: '{}'", expectedAction, returnedAction);
                        return false;
                    }
                }

                log.info("[TURNSTILE] Token Cloudflare verificado exitosamente.");
                return true;
            } else {
                log.error("[TURNSTILE] Código de estado inesperado de Cloudflare: {}", response.getStatusCode());
                return false;
            }
        } catch (Exception ex) {
            log.error("[TURNSTILE] Error al contactar la API de Cloudflare: {}", ex.getMessage());
            return false;
        }
    }

    private boolean isHostnameAllowed(String hostname, boolean isProd) {
        if (!isProd && ("example.com".equalsIgnoreCase(hostname) || "localhost".equalsIgnoreCase(hostname))) {
            return true;
        }
        if (allowedHostnamesStr == null || allowedHostnamesStr.isBlank()) {
            return true;
        }
        String[] allowed = allowedHostnamesStr.split(",");
        for (String a : allowed) {
            if (a.trim().equalsIgnoreCase(hostname.trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean isProductionProfile() {
        if (environment == null) return false;
        String[] profiles = environment.getActiveProfiles();
        for (String p : profiles) {
            if ("prod".equalsIgnoreCase(p) || "production".equalsIgnoreCase(p)) {
                return true;
            }
        }
        return false;
    }
}
