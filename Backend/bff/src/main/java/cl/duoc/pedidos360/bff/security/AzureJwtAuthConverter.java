package cl.duoc.pedidos360.bff.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * AzureJwtAuthConverter
 *
 * Convierte los claims del JWT de Microsoft Entra ID en GrantedAuthorities
 * que Spring Security puede usar para autorización.
 *
 * Transformaciones:
 *
 * 1. Roles de aplicación (claim "roles"):
 *    ["ADMIN", "VENDEDOR"] → [ROLE_ADMIN, ROLE_VENDEDOR]
 *    Permite usar: .hasRole("ADMIN")
 *
 * 2. Scopes delegados (claim "scp"):
 *    "access_as_user openid" → [SCOPE_access_as_user, SCOPE_openid]
 *    Permite usar: .hasAuthority("SCOPE_access_as_user")
 *
 * Logs:
 * [BFF][AUTH] usuario={oid}, roles=[...], scopes=[...]
 */
@Component
public class AzureJwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    // Converter base de Spring para claims "scope" y "scp"
    private final JwtGrantedAuthoritiesConverter defaultConverter = new JwtGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();

        // 1. Agregar scopes del claim "scp" (delegated permissions)
        //    Spring los convierte a SCOPE_xxx automáticamente
        authorities.addAll(defaultConverter.convert(jwt));

        // 2. Agregar roles del claim "roles" (application roles)
        //    Los convertimos a ROLE_XXX
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles != null) {
            for (String role : roles) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase()));
            }
        }

        // Log de auditoría (sin imprimir el token)
        String oid      = jwt.getClaimAsString("oid");
        String username = jwt.getClaimAsString("preferred_username");
        System.out.printf("[BFF][AUTH] usuario=%s oid=%s roles=%s authorities=%s%n",
                username, oid, roles, authorities);

        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}
