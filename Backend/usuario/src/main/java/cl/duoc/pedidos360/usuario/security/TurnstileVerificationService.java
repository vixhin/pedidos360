package cl.duoc.pedidos360.usuario.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class TurnstileVerificationService {

    private static final Logger log = LoggerFactory.getLogger(TurnstileVerificationService.class);
    private static final String VERIFY_URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final RestTemplate restTemplate;

    @Value("${turnstile.secret-key:${TURNSTILE_SECRET_KEY:1x0000000000000000000000000000000AA}}")
    private String secretKey;

    @Value("${turnstile.enabled:${TURNSTILE_ENABLED:true}}")
    private boolean turnstileEnabled;

    public TurnstileVerificationService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(4000);
        factory.setReadTimeout(4000);
        this.restTemplate = new RestTemplate(factory);
    }

    public TurnstileVerificationService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public boolean isEnabled() {
        return turnstileEnabled;
    }

    public boolean verifyToken(String token, String remoteIp) {
        if (!turnstileEnabled) {
            log.info("[TURNSTILE] Validation disabled by configuration.");
            return true;
        }

        if (token == null || token.trim().isEmpty()) {
            log.warn("[TURNSTILE] Verification rejected: missing or empty token.");
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
                Object successVal = response.getBody().get("success");
                boolean success = Boolean.TRUE.equals(successVal);
                if (success) {
                    log.info("[TURNSTILE] Cloudflare Turnstile token verified successfully.");
                    return true;
                } else {
                    Object errorCodes = response.getBody().get("error-codes");
                    log.warn("[TURNSTILE] Cloudflare rejected token: {}", errorCodes);
                    return false;
                }
            } else {
                log.error("[TURNSTILE] Unexpected response status from Cloudflare: {}", response.getStatusCode());
                return false;
            }
        } catch (Exception ex) {
            log.error("[TURNSTILE] Error verifying token with Cloudflare API: {}", ex.getMessage());
            return false;
        }
    }
}
