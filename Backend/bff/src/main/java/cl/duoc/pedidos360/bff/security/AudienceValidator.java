package cl.duoc.pedidos360.bff.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AudienceValidator
 *
 * Valida que el claim "aud" del JWT contenga el audience esperado.
 *
 * Spring Security valida automáticamente:
 *   - Firma    (via JWKS de Microsoft)
 *   - Issuer   (spring.security.oauth2.resourceserver.jwt.issuer-uri)
 *   - Exp      (automático)
 *
 * La audience NO se valida automáticamente por Spring en resource server mode,
 * por eso este validador es necesario.
 *
 * Valor esperado: ${bff.azure.api-audience} → api://<apiClientId>
 */
@Component
public class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_AUDIENCE = new OAuth2Error(
            "invalid_token",
            "El Access Token no contiene el audience requerido.",
            "https://tools.ietf.org/html/rfc6750#section-3.1"
    );

    @Value("${bff.azure.api-audience}")
    private String expectedAudience;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        List<String> audiences = jwt.getAudience();

        if (audiences == null || audiences.isEmpty()) {
            return OAuth2TokenValidatorResult.failure(INVALID_AUDIENCE);
        }

        boolean valid = audiences.stream()
                .anyMatch(aud -> aud.equals(expectedAudience));

        if (!valid) {
            return OAuth2TokenValidatorResult.failure(INVALID_AUDIENCE);
        }

        return OAuth2TokenValidatorResult.success();
    }
}
