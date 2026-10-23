package cl.duoc.pedidos360.carrito.listener;

import cl.duoc.pedidos360.carrito.config.RabbitMQConfig;
import cl.duoc.pedidos360.carrito.event.PedidoCreadoEvent;
import cl.duoc.pedidos360.carrito.service.CarritoService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CarritoEventListener {

    private static final Logger log = LoggerFactory.getLogger(CarritoEventListener.class);

    private final CarritoService carritoService;
    private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();

    public CarritoEventListener(CarritoService carritoService) {
        this.carritoService = carritoService;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_CART, ackMode = "MANUAL")
    public void handlePedidoCreado(PedidoCreadoEvent event,
                                    Channel channel,
                                    @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (event == null || event.getEventId() == null) {
            log.warn("[CARRITO-SERVICE] Received null or invalid event. Sending ACK to discard.");
            channel.basicAck(deliveryTag, false);
            return;
        }

        // Verificación de idempotencia
        if (processedEventIds.contains(event.getEventId())) {
            log.info("[CARRITO-SERVICE] Duplicate event ignored: eventId={}. Sending ACK.", event.getEventId());
            channel.basicAck(deliveryTag, false);
            return;
        }

        log.info("[CARRITO-SERVICE] Processing event: type={} eventId={} usuarioId={}",
                event.getEventType(), event.getEventId(), event.getUsuarioId());

        try {
            if (event.getUsuarioId() != null) {
                carritoService.vaciarCarrito(event.getUsuarioId());
                log.info("[CARRITO-SERVICE] Cart emptied successfully for usuarioId={} triggered by eventId={}",
                        event.getUsuarioId(), event.getEventId());
            }
            processedEventIds.add(event.getEventId());
            channel.basicAck(deliveryTag, false);
            log.debug("[CARRITO-SERVICE] ACK sent for eventId={} deliveryTag={}", event.getEventId(), deliveryTag);
        } catch (Exception ex) {
            log.error("[CARRITO-SERVICE] Error emptying cart for eventId={}: {}. Sending NACK (requeue=false, goes to DLQ).",
                    event.getEventId(), ex.getMessage(), ex);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    public boolean isEventProcessed(String eventId) {
        return processedEventIds.contains(eventId);
    }
}
