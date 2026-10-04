package cl.duoc.pedidos360.chat.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;

/**
 * ChatWebSocketController — Los mensajes de chat son enviados mediante HTTP REST (POST /api/chat/mensaje)
 * con identidad autenticada. El canal WebSocket/STOMP se utiliza únicamente para suscripciones
 * y difusión en tiempo real de nuevos mensajes y eventos de cierre.
 */
@Controller
public class ChatWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketController.class);

    public ChatWebSocketController() {
    }
}
