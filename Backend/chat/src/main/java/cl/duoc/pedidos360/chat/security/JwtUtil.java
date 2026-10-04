package cl.duoc.pedidos360.chat.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtUtil(@Value("${jwt.secret:pedidos360_secret_key_for_jwt_token_generation_2026_super_secure}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = getClaims(token);
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return validatePayloadExpiry(token);
        }
    }

    public String extractEmail(String token) {
        try {
            return getClaims(token).getSubject();
        } catch (Exception e) {
            return extractClaimFromPayload(token, "preferred_username", "email", "sub");
        }
    }

    public String extractRole(String token) {
        try {
            return getClaims(token).get("role", String.class);
        } catch (Exception e) {
            return extractClaimFromPayload(token, "roles", "role");
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean validatePayloadExpiry(String token) {
        try {
            Map<String, Object> payload = parsePayload(token);
            Number exp = (Number) payload.get("exp");
            if (exp == null) return false;
            long expMs = exp.longValue() * 1000L;
            return System.currentTimeMillis() < expMs;
        } catch (Exception e) {
            return false;
        }
    }

    private String extractClaimFromPayload(String token, String... keys) {
        try {
            Map<String, Object> payload = parsePayload(token);
            for (String key : keys) {
                Object val = payload.get(key);
                if (val != null) {
                    return val.toString();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePayload(String token) throws Exception {
        String[] parts = token.split("\\.");
        if (parts.length < 2) throw new IllegalArgumentException("Invalid JWT format");
        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        return objectMapper.readValue(payloadJson, Map.class);
    }
}
