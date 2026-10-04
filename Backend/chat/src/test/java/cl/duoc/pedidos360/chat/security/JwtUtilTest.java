package cl.duoc.pedidos360.chat.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private static final String SECRET = "pedidos360_secret_key_for_jwt_token_generation_2026_super_secure";
    private JwtUtil jwtUtil;
    private SecretKey key;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET);
        key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void testValidateToken_ValidHmacJwt_Success() {
        String token = Jwts.builder()
                .subject("usuario@pedidos360.cl")
                .claim("role", "CLIENTE")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(key)
                .compact();

        assertTrue(jwtUtil.validateToken(token));
        assertEquals("usuario@pedidos360.cl", jwtUtil.extractEmail(token));
        assertEquals("CLIENTE", jwtUtil.extractRole(token));
    }

    @Test
    void testValidateToken_InvalidSignature_Rejected() {
        SecretKey wrongKey = Keys.hmacShaKeyFor("a_very_different_wrong_secret_key_1234567890".getBytes(StandardCharsets.UTF_8));
        String forgedToken = Jwts.builder()
                .subject("hacker@pedidos360.cl")
                .claim("role", "ADMIN")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(wrongKey)
                .compact();

        assertFalse(jwtUtil.validateToken(forgedToken));
        assertNull(jwtUtil.extractEmail(forgedToken));
    }

    @Test
    void testValidateToken_ExpiredJwt_Rejected() {
        String expiredToken = Jwts.builder()
                .subject("usuario@pedidos360.cl")
                .claim("role", "CLIENTE")
                .issuedAt(new Date(System.currentTimeMillis() - 120000))
                .expiration(new Date(System.currentTimeMillis() - 60000))
                .signWith(key)
                .compact();

        assertFalse(jwtUtil.validateToken(expiredToken));
    }

    @Test
    void testValidateToken_UnsignedBase64PayloadOnly_Rejected() {
        // Unsigned header + payload with future exp
        String fakeHeader = java.util.Base64.getUrlEncoder().encodeToString("{\"alg\":\"none\"}".getBytes());
        String fakePayload = java.util.Base64.getUrlEncoder().encodeToString("{\"sub\":\"fake@user.com\",\"exp\":9999999999}".getBytes());
        String fakeToken = fakeHeader + "." + fakePayload + ".";

        assertFalse(jwtUtil.validateToken(fakeToken));
    }
}
