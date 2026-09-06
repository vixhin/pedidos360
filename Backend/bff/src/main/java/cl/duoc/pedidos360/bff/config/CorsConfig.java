package cl.duoc.pedidos360.bff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * CorsConfig — Configuración CORS para el BFF.
 *
 * Permite solicitudes desde el frontend Angular en desarrollo local (localhost:4200).
 * En producción, usa la variable FRONTEND_ORIGIN para restringir el origen.
 *
 * NO se usa "*" como origin cuando se requieren credenciales.
 */
@Configuration
public class CorsConfig {

    @Value("${bff.frontend-origin:http://localhost:4200}")
    private String frontendOrigin;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // Origen del frontend Angular (localhost:4200 en desarrollo)
        config.setAllowedOrigins(List.of(frontendOrigin));

        // Headers permitidos
        config.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept",
                "Origin",
                "X-Requested-With"
        ));

        // Métodos permitidos
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // Permitir credenciales (necesario para Authorization header)
        config.setAllowCredentials(true);

        // Pre-flight cache (1 hora)
        config.setMaxAge(3600L);

        // Exponer el header Authorization en la respuesta para el cliente
        config.setExposedHeaders(List.of("Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
