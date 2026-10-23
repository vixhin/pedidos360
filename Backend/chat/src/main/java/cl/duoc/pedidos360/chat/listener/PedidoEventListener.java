package cl.duoc.pedidos360.chat.listener;

import cl.duoc.pedidos360.chat.config.RabbitMQConfig;
import cl.duoc.pedidos360.chat.service.ChatService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

@Component
public class PedidoEventListener {

    private static final Logger log = LoggerFactory.getLogger(PedidoEventListener.class);
    private final ChatService chatService;

    public PedidoEventListener(ChatService chatService) {
        this.chatService = chatService;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_CHAT_PEDIDOS_ESTADO, ackMode = "MANUAL")
    public void handlePedidoEstadoActualizado(Map<String, Object> event,
                                               Channel channel,
                                               @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        log.info("[CHAT-SERVICE][RABBITMQ] Received domain event: {}", event);

        try {
            Number resourceIdObj = (Number) event.get("resourceId");
            String nuevoEstado = (String) event.get("nuevoEstado");

            if (resourceIdObj != null && "ENTREGADO".equalsIgnoreCase(nuevoEstado)) {
                Long pedidoId = resourceIdObj.longValue();
                log.info("[CHAT-SERVICE][RABBITMQ] Order ID={} delivered. Auto-closing chat...", pedidoId);
                chatService.cerrarConversacionPorPedidoEntregado(pedidoId);
            }

            channel.basicAck(deliveryTag, false);
            log.debug("[CHAT-SERVICE][RABBITMQ] ACK sent for deliveryTag={}", deliveryTag);
        } catch (Exception ex) {
            log.error("[CHAT-SERVICE][RABBITMQ] Error processing domain event: {}. Sending NACK (requeue=false, goes to DLQ).",
                    ex.getMessage(), ex);
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
