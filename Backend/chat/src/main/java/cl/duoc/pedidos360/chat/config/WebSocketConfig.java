package cl.duoc.pedidos360.chat.config;

import cl.duoc.pedidos360.chat.entity.Conversacion;
import cl.duoc.pedidos360.chat.repository.ConversacionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    @Value("${chat.frontend-origin:${FRONTEND_ORIGIN:http://localhost:4200}}")
    private String frontendOrigin;

    private final ObjectProvider<ConversacionRepository> conversacionRepositoryProvider;

    private final cl.duoc.pedidos360.chat.security.JwtUtil jwtUtil;

    public WebSocketConfig(ObjectProvider<ConversacionRepository> conversacionRepositoryProvider,
                           cl.duoc.pedidos360.chat.security.JwtUtil jwtUtil) {
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
                        String userIdHeader = accessor.getFirstNativeHeader("X-User-Id");
                        String userEmailHeader = accessor.getFirstNativeHeader("X-User-Email");

                        String authenticatedIdentity = null;

                        if (authHeader != null && authHeader.startsWith("Bearer ")) {
                            String token = authHeader.substring(7);
                            if (jwtUtil.validateToken(token)) {
                                authenticatedIdentity = jwtUtil.extractEmail(token);
                            }
                        }

                        if (authenticatedIdentity == null && userIdHeader != null && !userIdHeader.isBlank()) {
                            authenticatedIdentity = userIdHeader;
                        } else if (authenticatedIdentity == null && userEmailHeader != null && !userEmailHeader.isBlank()) {
                            authenticatedIdentity = userEmailHeader;
                        }

                        if (authenticatedIdentity == null || "anonymous".equalsIgnoreCase(authenticatedIdentity)) {
                            log.warn("[WS-CHAT] Connection rejected: No valid JWT token or identity provided.");
                            throw new IllegalArgumentException("Conexión WebSocket rechazada: Token JWT inválido o ausente.");
                        }

                        final String finalIdentity = authenticatedIdentity;
                        Principal principal = () -> finalIdentity;
                        accessor.setUser(principal);
                        log.info("[WS-CHAT] STOMP Client Connected: principal={}", finalIdentity);
                    } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                        String destination = accessor.getDestination();
                        if (destination != null && destination.startsWith("/topic/chat/")) {
                            String convIdStr = destination.replace("/topic/chat/", "").replace("/status", "");
                            try {
                                Long convId = Long.parseLong(convIdStr);
                                Principal user = accessor.getUser();
                                String principalName = user != null ? user.getName() : null;

                                ConversacionRepository repo = conversacionRepositoryProvider.getIfAvailable();
                                if (repo != null && principalName != null && !principalName.equals("anonymous")) {
                                    Optional<Conversacion> convOpt = repo.findById(convId);
                                    if (convOpt.isPresent()) {
                                        Conversacion conv = convOpt.get();
                                        try {
                                            Long userId = Long.parseLong(principalName);
                                            if (!userId.equals(conv.getClienteId()) && !userId.equals(conv.getRepartidorId())) {
                                                log.warn("[WS-CHAT] Subscription DENIED to topic {} for user ID={}", destination, userId);
                                                throw new IllegalArgumentException("No está autorizado para suscribirse a este chat.");
                                            }
                                        } catch (NumberFormatException e) {
                                            // email or string principal
                                        }
                                    }
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
                return message;
            }
        });
    }
}
