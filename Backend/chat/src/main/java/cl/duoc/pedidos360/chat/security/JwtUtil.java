package cl.duoc.pedidos360.chat.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);
    private final SecretKey hmacKey;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private final Map<String, PublicKey> msKeyCache = new ConcurrentHashMap<>();
    private long lastJwksFetch = 0;
    private static final long JWKS_CACHE_TTL_MS = 3600_000L;

    @Value("${azure.tenant-id:${AZURE_TENANT_ID:a50f6528-499a-4d94-bcad-ed9b200f7c7b}}")
    private String expectedTenantId;

    @Value("${azure.api-client-id:${AZURE_API_CLIENT_ID:5febc8e2-ee14-4452-8914-7d237eb5a6f5}}")
    private String expectedApiClientId;

    public JwtUtil(@Value("${jwt.secret:pedidos360_secret_key_for_jwt_token_generation_2026_super_secure}") String secret) {
        this.hmacKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) return false;
        try {
            Claims claims = parseAndVerify(token);
            if (claims == null) return false;
            Date exp = claims.getExpiration();
            if (exp != null && exp.before(new Date())) return false;
            Date nbf = claims.getNotBefore();
            if (nbf != null && nbf.after(new Date())) return false;
            return true;
        } catch (Exception e) {
            log.debug("[JWT-UTIL] Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    public String extractEmail(String token) {
        try {
            Claims claims = parseAndVerify(token);
            if (claims == null) return null;
            String email = claims.get("email", String.class);
            if (email != null && !email.isBlank()) return email;
            String preferredUsername = claims.get("preferred_username", String.class);
            if (preferredUsername != null && !preferredUsername.isBlank()) return preferredUsername;
            String upn = claims.get("upn", String.class);
            if (upn != null && !upn.isBlank()) return upn;
            return claims.getSubject();
        } catch (Exception e) {
            return null;
        }
    }

    public String extractRole(String token) {
        try {
            Claims claims = parseAndVerify(token);
            if (claims == null) return null;
            String role = claims.get("role", String.class);
            if (role != null && !role.isBlank()) return role;
            Object rolesObj = claims.get("roles");
            if (rolesObj != null) return rolesObj.toString();
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private Claims parseAndVerify(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(hmacKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception hmacEx) {
            return verifyMicrosoftToken(token);
        }
    }

    private Claims verifyMicrosoftToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 3) return null;

            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            JsonNode headerNode = objectMapper.readTree(headerJson);
            String alg = headerNode.has("alg") ? headerNode.get("alg").asText() : "";
            String kid = headerNode.has("kid") ? headerNode.get("kid").asText() : "";

            if (!"RS256".equalsIgnoreCase(alg) || kid.isBlank()) {
                return null;
            }

            PublicKey msPublicKey = getMicrosoftPublicKey(kid);
            if (msPublicKey == null) {
                return null;
            }

            Claims claims = Jwts.parser()
                    .verifyWith(msPublicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String iss = claims.getIssuer();
            if (iss == null || (!iss.contains("login.microsoftonline.com") && !iss.contains("sts.windows.net"))) {
                log.warn("[JWT-UTIL] Rejected MS token with invalid issuer: {}", iss);
                return null;
            }

            if (expectedTenantId != null && !expectedTenantId.isBlank() && !iss.contains(expectedTenantId)) {
                log.warn("[JWT-UTIL] Rejected MS token with tenant mismatch: iss={} expectedTenant={}", iss, expectedTenantId);
                return null;
            }

            Set<String> audSet = claims.getAudience();
            if (expectedApiClientId != null && !expectedApiClientId.isBlank()) {
                boolean validAud = audSet != null && audSet.stream().anyMatch(aud ->
                        aud.equals(expectedApiClientId) || aud.equals("api://" + expectedApiClientId));
                if (!validAud) {
                    log.warn("[JWT-UTIL] Rejected MS token with audience mismatch: aud={} expectedAud={}", audSet, expectedApiClientId);
                    return null;
                }
            }

            return claims;
        } catch (Exception e) {
            log.debug("[JWT-UTIL] Microsoft RSA validation failed: {}", e.getMessage());
            return null;
        }
    }

    private synchronized PublicKey getMicrosoftPublicKey(String kid) {
        if (msKeyCache.containsKey(kid) && (System.currentTimeMillis() - lastJwksFetch) < JWKS_CACHE_TTL_MS) {
            return msKeyCache.get(kid);
        }
        refreshJwksCache();
        return msKeyCache.get(kid);
    }

    private void refreshJwksCache() {
        try {
            String jwksUrl = "https://login.microsoftonline.com/common/discovery/v2.0/keys";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(jwksUrl))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode keys = root.get("keys");
                if (keys != null && keys.isArray()) {
                    Map<String, PublicKey> newCache = new HashMap<>();
                    for (JsonNode keyNode : keys) {
                        String kty = keyNode.path("kty").asText();
                        String kId = keyNode.path("kid").asText();
                        String nStr = keyNode.path("n").asText();
                        String eStr = keyNode.path("e").asText();

                        if ("RSA".equalsIgnoreCase(kty) && !kId.isBlank() && !nStr.isBlank() && !eStr.isBlank()) {
                            BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode(nStr));
                            BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode(eStr));
                            RSAPublicKeySpec spec = new RSAPublicKeySpec(n, e);
                            KeyFactory kf = KeyFactory.getInstance("RSA");
                            PublicKey pk = kf.generatePublic(spec);
                            newCache.put(kId, pk);
                        }
                    }
                    msKeyCache.putAll(newCache);
                    lastJwksFetch = System.currentTimeMillis();
                    log.info("[JWT-UTIL] Refreshed Microsoft JWKS cache, loaded {} keys", newCache.size());
                }
            }
        } catch (Exception e) {
            log.warn("[JWT-UTIL] Could not fetch Microsoft JWKS: {}", e.getMessage());
        }
    }
}
