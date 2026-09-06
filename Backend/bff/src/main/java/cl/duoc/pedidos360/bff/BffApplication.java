package cl.duoc.pedidos360.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * BFF (Backend for Frontend) — Pedidos360
 *
 * Responsabilidades:
 * 1. Validar el JWT (Access Token) emitido por Microsoft Entra ID.
 *    - Firma (RSA, JWKS de Microsoft)
 *    - Issuer (https://login.microsoftonline.com/{tenantId}/v2.0)
 *    - Audience (api://{apiClientId})
 *    - Expiración (claim exp)
 * 2. Aplicar autorización por roles (claim "roles") y scopes (claim "scp").
 * 3. Actuar como proxy hacia los microservicios internos (8081-8086).
 *
 * Puerto: 8090
 */
@SpringBootApplication
public class BffApplication {
    public static void main(String[] args) {
        SpringApplication.run(BffApplication.class, args);
    }
}
