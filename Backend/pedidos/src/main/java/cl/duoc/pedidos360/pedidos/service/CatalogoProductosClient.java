package cl.duoc.pedidos360.pedidos.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Consulta a productos-service el precio y stock vigentes de un producto.
 * Es la fuente de verdad de los precios: nunca se usan los enviados por el navegador.
 */
@Component
public class CatalogoProductosClient {

    /** Datos autoritativos de un producto del catálogo. */
    public record ProductoCatalogo(Long id, String nombre, double precio, int stock) {
    }

    private final RestTemplate restTemplate;
    private final String productosUrl;
    private final String internalKey;

    @Autowired
    public CatalogoProductosClient(
            @Value("${PRODUCTOS_SERVICE_URL:http://localhost:8085}") String productosUrl,
            @Value("${bff.internal-key:${BFF_INTERNAL_KEY:}}") String internalKey) {
        this(new RestTemplate(), productosUrl, internalKey);
    }

    CatalogoProductosClient(RestTemplate restTemplate, String productosUrl, String internalKey) {
        this.restTemplate = restTemplate;
        this.productosUrl = productosUrl;
        this.internalKey = internalKey;
    }

    /**
     * @throws IllegalArgumentException si el producto no existe
     * @throws IllegalStateException    si productos-service no responde
     */
    public ProductoCatalogo obtener(Long productoId) {
        HttpHeaders headers = new HttpHeaders();
        if (internalKey != null && !internalKey.isBlank()) {
            headers.set("X-Internal-Service-Key", internalKey);
        }
        try {
            ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
                    productosUrl + "/api/productos/" + productoId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<Map<String, Object>>() {});
            Map<String, Object> body = resp.getBody();
            Object data = body != null ? body.get("data") : null;
            if (!(data instanceof Map<?, ?> producto) || !(producto.get("precio") instanceof Number precio)) {
                throw new IllegalArgumentException("Producto no encontrado: " + productoId);
            }
            Object nombre = producto.get("nombre");
            int stock = producto.get("stock") instanceof Number s ? s.intValue() : 0;
            return new ProductoCatalogo(productoId, nombre != null ? nombre.toString() : null, precio.doubleValue(), stock);
        } catch (HttpClientErrorException.NotFound e) {
            throw new IllegalArgumentException("Producto no encontrado: " + productoId);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 400) {
                throw new IllegalArgumentException("Producto no encontrado: " + productoId);
            }
            throw new IllegalStateException("No se pudo validar el precio del producto " + productoId, e);
        } catch (RestClientException e) {
            throw new IllegalStateException("Catálogo de productos no disponible", e);
        }
    }
}
