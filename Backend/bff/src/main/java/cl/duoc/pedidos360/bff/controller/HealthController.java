package cl.duoc.pedidos360.bff.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HealthController — Endpoints de salud del BFF.
 *
 * /health  — El BFF está ejecutándose. No requiere JWT.
 * /ready   — El BFF puede comunicarse con los microservicios downstream.
 *            No requiere JWT.
 *
 * Estos endpoints están en el permitAll() de SecurityConfig.
 */
@RestController
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final WebClient productosClient;
    private final WebClient pedidosClient;

    public HealthController(
            @Qualifier("productosClient") WebClient productosClient,
            @Qualifier("pedidosClient")   WebClient pedidosClient) {
        this.productosClient = productosClient;
        this.pedidosClient   = pedidosClient;
    }

    /**
     * /health — BFF vivo y respondiendo.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "UP");
        response.put("service", "bff");
        response.put("timestamp", Instant.now().toString());
        return ResponseEntity.ok(response);
    }

    /**
     * /ready — BFF puede alcanzar al menos un microservicio downstream.
     * Prueba con productos-service (listar productos — endpoint público).
     */
    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        Map<String, Object> response = new LinkedHashMap<>();
        boolean productosOk = false;
        boolean pedidosOk   = false;

        try {
            productosClient.get().uri("/api/productos").retrieve()
                    .toBodilessEntity().block();
            productosOk = true;
        } catch (Exception e) {
            log.warn("[BFF][HEALTH] productos-service no disponible: {}", e.getMessage());
        }

        try {
            pedidosClient.get().uri("/api/pedidos").retrieve()
                    .toBodilessEntity().block();
            pedidosOk = true;
        } catch (Exception e) {
            log.warn("[BFF][HEALTH] pedidos-service no disponible: {}", e.getMessage());
        }

        boolean ready = productosOk || pedidosOk;
        response.put("status", ready ? "READY" : "NOT_READY");
        response.put("downstream", Map.of(
                "productos-service", productosOk ? "UP" : "DOWN",
                "pedidos-service",   pedidosOk   ? "UP" : "DOWN"
        ));
        response.put("timestamp", Instant.now().toString());

        return ResponseEntity
                .status(ready ? 200 : 503)
                .body(response);
    }
}
