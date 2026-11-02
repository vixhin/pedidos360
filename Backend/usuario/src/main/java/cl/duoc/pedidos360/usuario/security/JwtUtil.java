package cl.duoc.pedidos360.usuario.security;

import cl.duoc.pedidos360.usuario.enums.Rol;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    public static final String ISSUER = "pedidos360-usuario";
    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${auth.access-token-ttl:${jwt.expiration:PT15M}}") String expirationConfig) {
        if (secret == null || secret.trim().length() < 32) {
            throw new IllegalStateException("La propiedad 'jwt.secret' (o variable de entorno JWT_SECRET) es obligatoria y debe tener al menos 32 caracteres para garantizar la seguridad criptográfica (HMAC-SHA256).");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = parseExpirationMs(expirationConfig);
    }

    public JwtUtil(String secret, long expirationMs) {
        if (secret == null || secret.trim().length() < 32) {
            throw new IllegalStateException("La propiedad 'jwt.secret' (o variable de entorno JWT_SECRET) es obligatoria y debe tener al menos 32 caracteres para garantizar la seguridad criptográfica (HMAC-SHA256).");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    private long parseExpirationMs(String config) {
        try {
            if (config.startsWith("PT") || config.startsWith("P")) {
                return Duration.parse(config).toMillis();
            }
            return Long.parseLong(config);
        } catch (Exception e) {
            return 900_000L; // 15 minutos por defecto
        }
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public String generateToken(Long userId, String email, Rol rol, String sid) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .issuer(ISSUER)
                .subject(email)
                .claim("role", rol.name())
                .claim("uid", userId)
                .claim("sid", sid != null ? sid : UUID.randomUUID().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    // Método de sobrecarga para compatibilidad con llamadas y tests anteriores
    public String generateToken(String email, Rol rol) {
        return generateToken(1L, email, rol, UUID.randomUUID().toString());
    }

    public String extractEmail(String token) {
        return getClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return getClaims(token).get("role", String.class);
    }

    public Long extractUserId(String token) {
        Object uid = getClaims(token).get("uid");
        if (uid instanceof Number) {
            return ((Number) uid).longValue();
        }
        return null;
    }

    public String extractSessionId(String token) {
        return getClaims(token).get("sid", String.class);
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = getClaims(token);
            if (claims.getExpiration().before(new Date())) {
                return false;
            }
            // Validar issuer
            return ISSUER.equals(claims.getIssuer());
        } catch (Exception e) {
            return false;
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
