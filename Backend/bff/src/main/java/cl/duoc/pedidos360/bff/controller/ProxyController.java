package cl.duoc.pedidos360.bff.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * ProxyController — Enruta llamadas HTTP del frontend hacia los microservicios.
 *
 * El BFF actúa como intermediario:
 *   Angular → BFF (JWT validado) → Microservicio → PostgreSQL
 *
 * Endpoints expuestos:
 *   /api/bff/productos/**    → productos-service :8085
 *   /api/bff/pedidos/**      → pedidos-service   :8082
 *   /api/bff/carrito/**      → carrito-service   :8083
 *   /api/bff/notificaciones/**→notificacion-service :8086
 *   /api/bff/analitica/**    → analitica-service :8084
 *   /api/bff/usuarios/**     → usuario-service   :8081
 *
 * Propagación de identidad:
 * - El BFF NO reenvía el Access Token de Azure a los microservicios
 *   (los microservicios no tienen OAuth2 Resource Server configurado).
 * - El BFF puede enviar headers internos de identidad si se requiere:
 *   X-User-Sub, X-User-Roles (sin token).
 *
 * Logs:
 * [BFF][PROXY]      — inicio de llamada
 * [BFF][DOWNSTREAM] — resultado de la llamada
 */
@RestController
@RequestMapping("/api/bff")
public class ProxyController {

    private static final Logger log = LoggerFactory.getLogger(ProxyController.class);

    private final WebClient productosClient;
    private final WebClient pedidosClient;
    private final WebClient carritoClient;
    private final WebClient notificacionClient;
    private final WebClient analiticaClient;
    private final WebClient usuarioClient;

    public ProxyController(
            @Qualifier("productosClient")    WebClient productosClient,
            @Qualifier("pedidosClient")      WebClient pedidosClient,
            @Qualifier("carritoClient")      WebClient carritoClient,
            @Qualifier("notificacionClient") WebClient notificacionClient,
            @Qualifier("analiticaClient")    WebClient analiticaClient,
            @Qualifier("usuarioClient")      WebClient usuarioClient) {
        this.productosClient    = productosClient;
        this.pedidosClient      = pedidosClient;
        this.carritoClient      = carritoClient;
        this.notificacionClient = notificacionClient;
        this.analiticaClient    = analiticaClient;
        this.usuarioClient      = usuarioClient;
    }

    // ─── PRODUCTOS ──────────────────────────────────────────────────────────

    @RequestMapping(value = "/productos/**", method = {
            RequestMethod.GET, RequestMethod.POST,
            RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH
    })
    public ResponseEntity<String> proxiarProductos(
            HttpServletRequest request,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) String body) {
        return proxy(productosClient, request, jwt, body, "productos-service", "/api/bff/productos");
    }

    // ─── PEDIDOS ─────────────────────────────────────────────────────────────

    @RequestMapping(value = "/pedidos/**", method = {
            RequestMethod.GET, RequestMethod.POST,
            RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH
    })
    public ResponseEntity<String> proxiarPedidos(
            HttpServletRequest request,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) String body) {
        return proxy(pedidosClient, request, jwt, body, "pedidos-service", "/api/bff/pedidos");
    }

    // ─── CARRITO ─────────────────────────────────────────────────────────────

    @RequestMapping(value = "/carrito/**", method = {
            RequestMethod.GET, RequestMethod.POST,
            RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH
    })
    public ResponseEntity<String> proxiarCarrito(
            HttpServletRequest request,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) String body) {
        return proxy(carritoClient, request, jwt, body, "carrito-service", "/api/bff/carrito");
    }

    // ─── NOTIFICACIONES ──────────────────────────────────────────────────────

    @RequestMapping(value = "/notificaciones/**", method = {
            RequestMethod.GET, RequestMethod.POST,
            RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH
    })
    public ResponseEntity<String> proxiarNotificaciones(
            HttpServletRequest request,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) String body) {
        return proxy(notificacionClient, request, jwt, body, "notificacion-service", "/api/bff/notificaciones");
    }

    // ─── ANALÍTICA ───────────────────────────────────────────────────────────

    @RequestMapping(value = "/analitica/**", method = {
            RequestMethod.GET, RequestMethod.POST,
            RequestMethod.PUT, RequestMethod.DELETE
    })
    public ResponseEntity<String> proxiarAnalitica(
            HttpServletRequest request,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) String body) {
        return proxy(analiticaClient, request, jwt, body, "analitica-service", "/api/bff/analitica");
    }

    // ─── USUARIOS ────────────────────────────────────────────────────────────

    @RequestMapping(value = "/usuarios/**", method = {
            RequestMethod.GET, RequestMethod.POST,
            RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH
    })
    public ResponseEntity<String> proxiarUsuarios(
            HttpServletRequest request,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) String body) {
        return proxy(usuarioClient, request, jwt, body, "usuario-service", "/api/bff/usuarios");
    }

    // ─── HELPER GENÉRICO ────────────────────────────────────────────────────

    /**
     * Proxy genérico que:
     * 1. Extrae el path relativo al microservicio
     * 2. Propaga la query string
     * 3. Agrega headers de identidad (X-User-Sub, X-User-Roles) — NO el token
     * 4. Hace la llamada al microservicio via WebClient
     * 5. Devuelve la respuesta al cliente
     */
    private ResponseEntity<String> proxy(
            WebClient client,
            HttpServletRequest request,
            Jwt jwt,
            String body,
            String serviceName,
            String bffPathPrefix) {

        // Construir el path downstream mapeando de /api/bff/xxx a /api/xxx
        String requestUri  = request.getRequestURI();
        String relativePath = requestUri.replace(bffPathPrefix, "");
        // Los microservicios usan /api/productos, /api/pedidos, /api/usuario (singular), etc.
        String serviceBasePath = "/api/" + serviceName.replace("-service", "");
        String downstreamPath = serviceBasePath + relativePath;

        String queryString = request.getQueryString();
        String fullPath    = queryString != null ? downstreamPath + "?" + queryString : downstreamPath;

        // Método HTTP
        HttpMethod method = HttpMethod.valueOf(request.getMethod());

        // Log (sin token)
        String userSub   = jwt != null ? jwt.getSubject() : "anonymous";
        String userRoles = jwt != null ? String.valueOf(jwt.getClaim("roles")) : "[]";
        log.info("[BFF][PROXY] {} {} | user={} | target={}{}", method, requestUri, userSub, serviceName, fullPath);

        try {
            WebClient.RequestBodySpec requestSpec = client
                    .method(method)
                    .uri(fullPath)
                    // Headers de identidad propagados al microservicio (sin el token)
                    .header("X-User-Sub",   userSub)
                    .header("X-User-Roles", userRoles)
                    .header("Content-Type", "application/json");

            String responseBody;
            if (body != null && !body.isBlank()) {
                responseBody = requestSpec
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();
            } else {
                responseBody = requestSpec
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();
            }

            log.info("[BFF][DOWNSTREAM] {} | {} → 200 OK", serviceName, fullPath);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(responseBody);

        } catch (WebClientResponseException ex) {
            log.warn("[BFF][DOWNSTREAM] {} | {} → {} | reason={}",
                    serviceName, fullPath, ex.getStatusCode(), ex.getMessage());
            return ResponseEntity
                    .status(ex.getStatusCode())
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .body(ex.getResponseBodyAsString());
        } catch (Exception ex) {
            log.error("[BFF][DOWNSTREAM] {} | {} → 503 | error={}", serviceName, fullPath, ex.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Servicio temporalmente no disponible: " + serviceName);
        }
    }
}
