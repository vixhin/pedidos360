package cl.duoc.pedidos360.bff.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * ProxyController — Enruta llamadas HTTP del frontend hacia los microservicios.
 *
 * El BFF actúa como intermediario:
 *   Angular → BFF (JWT validado) → Microservicio → PostgreSQL
 *
 * Endpoints expuestos:
 *   /api/bff/productos/**     → productos-service :8085
 *   /api/bff/pedidos/**       → pedidos-service   :8082
 *   /api/bff/carrito/**       → carrito-service   :8083
 *   /api/bff/notificaciones/**→ notificacion-service :8086
 *   /api/bff/analitica/**     → analitica-service :8084
 *   /api/bff/usuarios/**      → usuario-service   :8081
 *   /api/bff/chat/**          → chat-service      :8091
 *
 * Propagación de identidad:
 * - El BFF NO reenvía el Access Token de Azure a los microservicios
 *   (los microservicios no tienen OAuth2 Resource Server configurado).
 * - El BFF envía headers internos de identidad si se requiere:
 *   X-User-Sub, X-User-Roles, X-User-Email (sin token).
 *
 * Logs:
 * [BFF][PROXY]      — inicio de llamada
 * [BFF][DOWNSTREAM] — resultado de la llamada con status real
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
    private final WebClient chatClient;

    @org.springframework.beans.factory.annotation.Value("${bff.internal-key:${BFF_INTERNAL_KEY:}}")
    private String bffInternalKey;

    public ProxyController(
            @Qualifier("productosClient")    WebClient productosClient,
            @Qualifier("pedidosClient")      WebClient pedidosClient,
            @Qualifier("carritoClient")      WebClient carritoClient,
            @Qualifier("notificacionClient") WebClient notificacionClient,
            @Qualifier("analiticaClient")    WebClient analiticaClient,
            @Qualifier("usuarioClient")      WebClient usuarioClient,
            @Qualifier("chatClient")         WebClient chatClient) {
        this.productosClient    = productosClient;
        this.pedidosClient      = pedidosClient;
        this.carritoClient      = carritoClient;
        this.notificacionClient = notificacionClient;
        this.analiticaClient    = analiticaClient;
        this.usuarioClient      = usuarioClient;
        this.chatClient         = chatClient;
    }

    // ─── CHAT ───────────────────────────────────────────────────────────────

    @RequestMapping(value = "/chat/**", method = {
            RequestMethod.GET, RequestMethod.POST,
            RequestMethod.PUT, RequestMethod.DELETE
    })
    public ResponseEntity<String> proxiarChat(
            HttpServletRequest request,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) String body) {
        return proxy(chatClient, request, jwt, body, "chat-service", "/api/bff/chat");
    }

    // ─── PRODUCTOS ──────────────────────────────────────────────────────────

    @RequestMapping(value = "/productos/**", method = {
            RequestMethod.GET, RequestMethod.POST,
            RequestMethod.PUT, RequestMethod.DELETE
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
            RequestMethod.PUT, RequestMethod.DELETE
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
            RequestMethod.PUT, RequestMethod.DELETE
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
            RequestMethod.PUT, RequestMethod.DELETE
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
            RequestMethod.PUT, RequestMethod.DELETE
    })
    public ResponseEntity<String> proxiarUsuarios(
            HttpServletRequest request,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) String body) {
        return proxy(usuarioClient, request, jwt, body, "usuario-service", "/api/bff/usuarios");
    }

    // ─── RABBITMQ ADMIN ──────────────────────────────────────────────────────

    @RequestMapping(value = "/admin/rabbit/**", method = {
            RequestMethod.GET, RequestMethod.POST,
            RequestMethod.PUT, RequestMethod.DELETE
    })
    public ResponseEntity<String> proxiarRabbitAdmin(
            HttpServletRequest request,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody(required = false) String body) {
        return proxy(usuarioClient, request, jwt, body, "usuario-service", "/api/bff/admin/rabbit", "/api/admin/rabbit");
    }

    // ─── HELPER GENÉRICO ────────────────────────────────────────────────────

    /**
     * Proxy genérico que:
     * 1. Extrae el path relativo al microservicio
     * 2. Propaga la query string
     * 3. Agrega headers de identidad (X-User-Sub, X-User-Roles, X-User-Email) — NO el token
     * 4. Hace la llamada al microservicio via WebClient con exchangeToMono
     * 5. Preserva el código de estado HTTP real (200, 201, 204, 4xx, 5xx) y Content-Type
     * 6. Retorna 503 solo ante fallo de conexión / infraestructura
     */
    private ResponseEntity<String> proxy(
            WebClient client,
            HttpServletRequest request,
            Jwt jwt,
            String body,
            String serviceName,
            String bffPathPrefix) {
        String serviceBasePath = "/api/" + serviceName.replace("-service", "");
        return proxy(client, request, jwt, body, serviceName, bffPathPrefix, serviceBasePath);
    }

    private ResponseEntity<String> proxy(
            WebClient client,
            HttpServletRequest request,
            Jwt jwt,
            String body,
            String serviceName,
            String bffPathPrefix,
            String serviceBasePath) {

        // Construir el path downstream mapeando de /api/bff/xxx a downstream path
        String requestUri  = request.getRequestURI();
        String relativePath = requestUri.replace(bffPathPrefix, "");
        String downstreamPath = serviceBasePath + relativePath;

        String queryString = request.getQueryString();
        String fullPath    = queryString != null ? downstreamPath + "?" + queryString : downstreamPath;

        // Método HTTP
        HttpMethod method = HttpMethod.valueOf(request.getMethod());

        String userSub = jwt != null ? jwt.getSubject() : "anonymous";
        String userEmail = (jwt != null && jwt.getClaimAsString("email") != null) ? jwt.getClaimAsString("email") : userSub;
        java.util.List<String> roles = (jwt != null) ? jwt.getClaimAsStringList("roles") : null;
        String userRoles = (roles != null) ? roles.toString() : "[]";
        log.info("[BFF][PROXY] {} {} | user={} | target={}{}", method, requestUri, userSub, serviceName, fullPath);

        try {
            WebClient.RequestBodySpec requestSpec = client
                    .method(method)
                    .uri(fullPath)
                    .header("X-User-Sub",   userSub)
                    .header("X-User-Email", userEmail)
                    .header("X-User-Roles", userRoles)
                    .header("Content-Type", "application/json");

            if (bffInternalKey != null && !bffInternalKey.isBlank()) {
                requestSpec.header("X-Internal-Service-Key", bffInternalKey);
            }

            WebClient.RequestHeadersSpec<?> headersSpec;
            if (body != null && !body.isBlank()) {
                headersSpec = requestSpec.bodyValue(body);
            } else {
                headersSpec = requestSpec;
            }

            return headersSpec
                    .exchangeToMono(clientResponse -> {
                        HttpStatusCode status = clientResponse.statusCode();
                        MediaType contentType = clientResponse.headers()
                                .contentType()
                                .orElse(MediaType.APPLICATION_JSON);

                        return clientResponse.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(responseBody -> {
                                    ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
                                    if (status.value() == 204 || responseBody.isBlank()) {
                                        log.info("[BFF][DOWNSTREAM] {} | {} → {} (empty body)", serviceName, fullPath, status);
                                        return builder.<String>build();
                                    }
                                    log.info("[BFF][DOWNSTREAM] {} | {} → {}", serviceName, fullPath, status);
                                    return builder
                                            .contentType(contentType)
                                            .body(responseBody);
                                });
                    })
                    .block();

        } catch (Exception ex) {
            log.error("[BFF][DOWNSTREAM] {} | {} → 503 | error={}", serviceName, fullPath, ex.getMessage());
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Servicio temporalmente no disponible: " + serviceName);
        }
    }
}
