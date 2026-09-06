package cl.duoc.pedidos360.bff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClientConfig — Configura WebClient para llamadas HTTP a los microservicios.
 *
 * Cada microservicio tiene su propio WebClient con la URL base configurada.
 * Las URLs provienen de application.properties (con variables de entorno).
 *
 * En Docker: se usan nombres de servicios (usuario, pedidos, etc.)
 * En desarrollo local: se usa localhost
 */
@Configuration
public class WebClientConfig {

    @Value("${services.usuario-url:http://localhost:8081}")
    private String usuarioUrl;

    @Value("${services.pedidos-url:http://localhost:8082}")
    private String pedidosUrl;

    @Value("${services.carrito-url:http://localhost:8083}")
    private String carritoUrl;

    @Value("${services.analitica-url:http://localhost:8084}")
    private String analiticaUrl;

    @Value("${services.productos-url:http://localhost:8085}")
    private String productosUrl;

    @Value("${services.notificacion-url:http://localhost:8086}")
    private String notificacionUrl;

    @Bean(name = "usuarioClient")
    public WebClient usuarioClient() {
        return WebClient.builder().baseUrl(usuarioUrl).build();
    }

    @Bean(name = "pedidosClient")
    public WebClient pedidosClient() {
        return WebClient.builder().baseUrl(pedidosUrl).build();
    }

    @Bean(name = "carritoClient")
    public WebClient carritoClient() {
        return WebClient.builder().baseUrl(carritoUrl).build();
    }

    @Bean(name = "analiticaClient")
    public WebClient analiticaClient() {
        return WebClient.builder().baseUrl(analiticaUrl).build();
    }

    @Bean(name = "productosClient")
    public WebClient productosClient() {
        return WebClient.builder().baseUrl(productosUrl).build();
    }

    @Bean(name = "notificacionClient")
    public WebClient notificacionClient() {
        return WebClient.builder().baseUrl(notificacionUrl).build();
    }
}
