package cl.duoc.pedidos360.chat.websocket;

import cl.duoc.pedidos360.chat.dto.EnviarMensajeDTO;
import cl.duoc.pedidos360.chat.dto.MensajeResponseDTO;
import cl.duoc.pedidos360.chat.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Controller
public class ChatWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketController.class);
    private final ChatService chatService;

    public ChatWebSocketController(ChatService chatService) {
        this.chatService = chatService;
    }

    @MessageMapping("/chat.sendMessage")
    public MensajeResponseDTO processMessage(@Payload EnviarMensajeDTO dto) {
        log.info("[CHAT-SERVICE][WEBSOCKET] Received STOMP message for conversacionId={}: {}", dto.getConversacionId(), dto.getContenido());
        return chatService.enviarMensaje(dto);
    }
}
