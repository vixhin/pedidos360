package cl.duoc.pedidos360.usuario.security;

import cl.duoc.pedidos360.usuario.enums.Rol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil("pedidos360_secret_key_for_jwt_token_generation_2026_super_secure", 86400000L);
    }

    @Test
    @DisplayName("generateToken - genera token válido")
    void testGenerateToken() {
        String token = jwtUtil.generateToken("user@pedidos360.cl", Rol.ADMIN);

        assertThat(token).isNotBlank();
        assertThat(jwtUtil.validateToken(token)).isTrue();
        assertThat(jwtUtil.extractEmail(token)).isEqualTo("user@pedidos360.cl");
        assertThat(jwtUtil.extractRole(token)).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("validateToken - retorna false para token malformado")
    void testValidateTokenInvalido() {
        assertThat(jwtUtil.validateToken("invalid.token.string")).isFalse();
    }
}
