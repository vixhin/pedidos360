package cl.duoc.pedidos360.chat.config;

import cl.duoc.pedidos360.chat.entity.Conversacion;
import cl.duoc.pedidos360.chat.repository.ConversacionRepository;
import cl.duoc.pedidos360.chat.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    @Value("${chat.frontend-origin:${FRONTEND_ORIGIN:http://localhost:4200}}")
    private String frontendOrigin;

    @Value("${bff.internal-key:${BFF_INTERNAL_KEY:pedidos360-internal-secret-key-local-2026}}")
    private String bffInternalKey;

    @Value("${services.usuario-url:${USUARIO_SERVICE_URL:http://localhost:8081}}")
    private String usuarioServiceUrl;

    private final ObjectProvider<ConversacionRepository> conversacionRepositoryProvider;
    private final JwtUtil jwtUtil;
    private final RestTemplate restTemplate = new RestTemplate();

    public WebSocketConfig(ObjectProvider<ConversacionRepository> conversacionRepositoryProvider,
                           JwtUtil jwtUtil) {
        this.conversacionRepositoryProvider = conversacionRepositoryProvider;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Habilita un broker simple en memoria para los prefijos /topic y /queue
        registry.enableSimpleBroker("/topic", "/queue");
        // Prefijo para los mensajes enviados desde los clientes hacia los métodos @MessageMapping
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Endpoint WebSocket nativo y SockJS para la app Angular (sin origins salvajes "*")
        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPatterns("http://localhost:*", frontendOrigin);
        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPatterns("http://localhost:*", frontendOrigin)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null) {
                    if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                        String authHeader = accessor.getFirstNativeHeader("Authorization");

                        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                            log.warn("[WS-CHAT] Connection rejected: Missing Authorization header.");
                            throw new IllegalArgumentException("Conexión WebSocket rechazada: Token JWT requerido.");
                        }

                        String token = authHeader.substring(7);
                        if (!jwtUtil.validateToken(token)) {
                            log.warn("[WS-CHAT] Connection rejected: Invalid or expired JWT token.");
                            throw new IllegalArgumentException("Conexión WebSocket rechazada: Token JWT inválido o expirado.");
                        }

                        String authenticatedIdentity = jwtUtil.extractEmail(token);
                        if (authenticatedIdentity == null || authenticatedIdentity.isBlank() || "anonymous".equalsIgnoreCase(authenticatedIdentity)) {
                            log.warn("[WS-CHAT] Connection rejected: Token missing subject identity.");
                            throw new IllegalArgumentException("Conexión WebSocket rechazada: Token sin identidad de usuario.");
                        }

                        final String finalIdentity = authenticatedIdentity;
                        Principal principal = () -> finalIdentity;
                        accessor.setUser(principal);
                        log.info("[WS-CHAT] STOMP Client Connected: principal={}", finalIdentity);
                    } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                        String destination = accessor.getDestination();
                        if (destination != null && destination.startsWith("/topic/chat/")) {
                            Principal user = accessor.getUser();
                            if (user == null || user.getName() == null || user.getName().isBlank() || "anonymous".equalsIgnoreCase(user.getName())) {
                                log.warn("[WS-CHAT] Subscription DENIED: Anonymous or missing principal for topic {}", destination);
                                throw new IllegalArgumentException("No está autorizado para suscribirse a este chat.");
                            }

                            String convIdStr = destination.replace("/topic/chat/", "").replace("/status", "");
                            try {
                                Long convId = Long.parseLong(convIdStr);
                                String principalEmail = user.getName();
                                Long userId = resolveUserIdByEmail(principalEmail);

                                if (userId == null) {
                                    log.warn("[WS-CHAT] Subscription DENIED: Could not resolve user ID for email={} topic={}", principalEmail, destination);
                                    throw new IllegalArgumentException("Acceso denegado: Usuario no encontrado.");
                                }

                                ConversacionRepository repo = conversacionRepositoryProvider.getIfAvailable();
                                if (repo != null) {
                                    Optional<Conversacion> convOpt = repo.findById(convId);
                                    if (convOpt.isPresent()) {
                                        Conversacion conv = convOpt.get();
                                        if (!userId.equals(conv.getClienteId()) && !userId.equals(conv.getRepartidorId())) {
                                            log.warn("[WS-CHAT] Subscription DENIED to topic {} for resolved user ID={}", destination, userId);
                                            throw new IllegalArgumentException("No está autorizado para suscribirse a este chat.");
                                        }
                                    } else {
                                        log.warn("[WS-CHAT] Subscription DENIED: Conversation ID={} not found", convId);
                                        throw new IllegalArgumentException("Conversación no encontrada.");
                                    }
                                }
                            } catch (NumberFormatException e) {
                                log.warn("[WS-CHAT] Subscription DENIED: Invalid topic format {}", destination);
                                throw new IllegalArgumentException("Formato de tópico inválido.");
                            }
                        }
                    }
                }
                return message;
            }
        });
    }

    private Long resolveUserIdByEmail(String emailOrId) {
        if (emailOrId == null || emailOrId.isBlank()) return null;
        try {
            return Long.parseLong(emailOrId);
        } catch (NumberFormatException ignored) {}

        try {
            String url = usuarioServiceUrl + "/api/internal/usuario/by-email?email=" + emailOrId;
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Internal-Service-Key", bffInternalKey);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map data = (Map) response.getBody().get("data");
                if (data != null && data.get("id") != null) {
                    return ((Number) data.get("id")).longValue();
                }
            }
        } catch (Exception e) {
            log.warn("[WS-CHAT] Could not resolve userId by email={}: {}", emailOrId, e.getMessage());
        }
        return null;
    }
}
