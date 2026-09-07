package cl.duoc.pedidos360.bff.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AzureJwtAuthConverterTest {

    @Test
    @DisplayName("convert - convierte roles de Azure AD a ROLE_XXX authorities")
    void testConvertRoles() {
        AzureJwtAuthConverter converter = new AzureJwtAuthConverter();

        Jwt jwt = new Jwt(
                "token-content",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "RS256"),
                Map.of(
                        "sub", "user-oid-123",
                        "preferred_username", "admin@pedidos360.cl",
                        "roles", List.of("ADMIN", "VENDEDOR"),
                        "scp", "access_as_user"
                )
        );

        AbstractAuthenticationToken auth = converter.convert(jwt);

        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities())
                .extracting("authority")
                .contains("ROLE_ADMIN", "ROLE_VENDEDOR", "SCOPE_access_as_user");
    }
}
