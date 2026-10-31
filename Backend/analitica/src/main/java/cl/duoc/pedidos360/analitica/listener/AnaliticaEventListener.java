package cl.duoc.pedidos360.analitica.listener;

import cl.duoc.pedidos360.analitica.config.RabbitMQConfig;
import cl.duoc.pedidos360.analitica.entity.AnaliticaEvento;
import cl.duoc.pedidos360.analitica.event.CarritoExpiradoEvent;
import cl.duoc.pedidos360.analitica.event.PedidoCreadoEvent;
import cl.duoc.pedidos360.analitica.event.PedidoEstadoActualizadoEvent;
import cl.duoc.pedidos360.analitica.event.RepartidorAsignadoEvent;
import cl.duoc.pedidos360.analitica.event.UsuarioLoginExitosoEvent;
import cl.duoc.pedidos360.analitica.event.UsuarioLoginFallidoEvent;
import cl.duoc.pedidos360.analitica.service.AnaliticaService;
import cl.duoc.pedidos360.analitica.service.IdempotencyService;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RabbitListener(queues = RabbitMQConfig.QUEUE_ANALYTICS, ackMode = "MANUAL")
public class AnaliticaEventListener {

    private static final Logger log = LoggerFactory.getLogger(AnaliticaEventListener.class);

    private final AnaliticaService analiticaService;
    private final IdempotencyService idempotencyService;
    private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();

    public AnaliticaEventListener(AnaliticaService analiticaService) {
        this(analiticaService, null);
    }

    @Autowired
    public AnaliticaEventListener(AnaliticaService analiticaService,
                                  @Autowired(required = false) IdempotencyService idempotencyService) {
        this.analiticaService = analiticaService;
        this.idempotencyService = idempotencyService;
    }

    private boolean isDuplicate(String eventId) {
        if (eventId == null) return false;
        if (processedEventIds.contains(eventId)) return true;
        if (idempotencyService != null && idempotencyService.isAlreadyProcessed(eventId)) {
            processedEventIds.add(eventId);
            return true;
        }
        return false;
    }

    private void recordProcessed(String eventId, String eventType, String resourceId) {
        if (eventId == null) return;
        processedEventIds.add(eventId);
        if (idempotencyService != null) {
            idempotencyService.markAsProcessed(eventId, "RABBITMQ", eventType, resourceId, null, "analitica.queue");
        }
    }

    @RabbitHandler
    public void handlePedidoCreado(PedidoCreadoEvent event,
                                    Channel channel,
                                    @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (event == null || event.getEventId() == null) {
            channel.basicAck(deliveryTag, false);
            return;
        }

        if (isDuplicate(event.getEventId())) {
            log.info("[ANALITICA-SERVICE] Duplicate event ignored (already processed via Kafka/RabbitMQ): eventId={}. Sending ACK.", event.getEventId());
            channel.basicAck(deliveryTag, false);
            return;
        }

        log.info("[ANALITICA-SERVICE] Recording analytics metric for event: type={} eventId={}",
                event.getEventType(), event.getEventId());

        try {
            String descripcion = String.format("Pedido #%d creado por usuarioId=%d por un total de $%.0f",
                    event.getResourceId(), event.getUsuarioId(), event.getTotal() != null ? event.getTotal() : 0.0);

            AnaliticaEvento analiticaEvento = new AnaliticaEvento(null, "PEDIDO_CREADO", descripcion);
            analiticaService.registrarEvento(analiticaEvento);

            recordProcessed(event.getEventId(), event.getEventType(), event.getResourceId() != null ? event.getResourceId().toString() : null);
            channel.basicAck(deliveryTag, false);
            log.info("[ANALITICA-SERVICE] ACK sent. Event recorded successfully: eventId={}", event.getEventId());
        } catch (Exception ex) {
            log.error("[ANALITICA-SERVICE] Error processing PedidoCreado eventId={}: {}. NACK (requeue=false).",
                    event.getEventId(), ex.getMessage(), ex);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    @RabbitHandler
    public void handleCarritoExpirado(CarritoExpiradoEvent event,
                                       Channel channel,
                                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (event == null || event.getEventId() == null) {
            channel.basicAck(deliveryTag, false);
            return;
        }

        if (isDuplicate(event.getEventId())) {
            log.info("[ANALITICA-SERVICE] Duplicate event ignored: eventId={}. Sending ACK.", event.getEventId());
            channel.basicAck(deliveryTag, false);
            return;
        }

        log.info("[ANALITICA-SERVICE] Recording analytics metric for event: type={} eventId={}",
                event.getEventType(), event.getEventId());

        try {
            String descripcion = String.format("Carrito de usuarioId=%d expirado automaticamente tras 48 hrs de inactividad",
                    event.getUsuarioId());

            AnaliticaEvento analiticaEvento = new AnaliticaEvento(null, "CARRITO_EXPIRADO", descripcion);
            analiticaService.registrarEvento(analiticaEvento);

            recordProcessed(event.getEventId(), event.getEventType(), event.getUsuarioId() != null ? event.getUsuarioId().toString() : null);
            channel.basicAck(deliveryTag, false);
            log.info("[ANALITICA-SERVICE] ACK sent. Event recorded successfully: eventId={}", event.getEventId());
        } catch (Exception ex) {
            log.error("[ANALITICA-SERVICE] Error processing CarritoExpirado eventId={}: {}. NACK (requeue=false).",
                    event.getEventId(), ex.getMessage(), ex);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    @RabbitHandler
    public void handlePedidoEstadoActualizado(PedidoEstadoActualizadoEvent event,
                                               Channel channel,
                                               @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (event == null || event.getEventId() == null) {
            channel.basicAck(deliveryTag, false);
            return;
        }

        if (isDuplicate(event.getEventId())) {
            log.info("[ANALITICA-SERVICE] Duplicate event ignored (already processed via Kafka/RabbitMQ): eventId={}. Sending ACK.", event.getEventId());
            channel.basicAck(deliveryTag, false);
            return;
        }

        log.info("[ANALITICA-SERVICE] Recording analytics metric for event: type={} eventId={}",
                event.getEventType(), event.getEventId());

        try {
            String descripcion = String.format("Pedido #%d cambio de estado a '%s' (usuarioId=%d)",
                    event.getResourceId(), event.getNuevoEstado(), event.getUsuarioId());

            AnaliticaEvento analiticaEvento = new AnaliticaEvento(null, "PEDIDO_ESTADO_ACTUALIZADO", descripcion);
            analiticaService.registrarEvento(analiticaEvento);

            recordProcessed(event.getEventId(), event.getEventType(), event.getResourceId() != null ? event.getResourceId().toString() : null);
            channel.basicAck(deliveryTag, false);
            log.info("[ANALITICA-SERVICE] ACK sent. Event recorded successfully: eventId={}", event.getEventId());
        } catch (Exception ex) {
            log.error("[ANALITICA-SERVICE] Error processing PedidoEstadoActualizado eventId={}: {}. NACK (requeue=false).",
                    event.getEventId(), ex.getMessage(), ex);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    @RabbitHandler
    public void handleRepartidorAsignado(RepartidorAsignadoEvent event,
                                          Channel channel,
                                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (event == null || event.getEventId() == null) {
            channel.basicAck(deliveryTag, false);
            return;
        }

        if (isDuplicate(event.getEventId())) {
            log.info("[ANALITICA-SERVICE] Duplicate event ignored (already processed via Kafka/RabbitMQ): eventId={}. Sending ACK.", event.getEventId());
            channel.basicAck(deliveryTag, false);
            return;
        }

        log.info("[ANALITICA-SERVICE] Recording analytics metric for event: type={} eventId={}",
                event.getEventType(), event.getEventId());

        try {
            String descripcion = String.format("Repartidor %s (ID=%d) asignado a pedido #%d",
                    event.getNombreRepartidor() != null ? event.getNombreRepartidor() : "desconocido",
                    event.getRepartidorId(), event.getResourceId());

            AnaliticaEvento analiticaEvento = new AnaliticaEvento(null, "REPARTIDOR_ASIGNADO", descripcion);
            analiticaService.registrarEvento(analiticaEvento);

            recordProcessed(event.getEventId(), event.getEventType(), event.getResourceId() != null ? event.getResourceId().toString() : null);
            channel.basicAck(deliveryTag, false);
            log.info("[ANALITICA-SERVICE] ACK sent. Event recorded successfully: eventId={}", event.getEventId());
        } catch (Exception ex) {
            log.error("[ANALITICA-SERVICE] Error processing RepartidorAsignado eventId={}: {}. NACK (requeue=false).",
                    event.getEventId(), ex.getMessage(), ex);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    @RabbitHandler
    public void handleUsuarioLoginExitoso(UsuarioLoginExitosoEvent event,
                                           Channel channel,
                                           @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (event == null || event.getEventId() == null) {
            channel.basicAck(deliveryTag, false);
            return;
        }

        if (isDuplicate(event.getEventId())) {
            log.info("[ANALITICA-SERVICE] Duplicate event ignored: eventId={}. Sending ACK.", event.getEventId());
            channel.basicAck(deliveryTag, false);
            return;
        }

        log.info("[ANALITICA-SERVICE] Recording analytics metric for event: type={} eventId={}",
                event.getEventType(), event.getEventId());

        try {
            String descripcion = String.format("Inicio de sesion exitoso: usuarioId=%d email=%s rol=%s",
                    event.getUsuarioId(), event.getEmail(), event.getRol());

            AnaliticaEvento analiticaEvento = new AnaliticaEvento(null, "USUARIO_LOGIN_EXITOSO", descripcion);
            analiticaService.registrarEvento(analiticaEvento);

            recordProcessed(event.getEventId(), event.getEventType(), event.getUsuarioId() != null ? event.getUsuarioId().toString() : null);
            channel.basicAck(deliveryTag, false);
            log.info("[ANALITICA-SERVICE] ACK sent. Event recorded successfully: eventId={}", event.getEventId());
        } catch (Exception ex) {
            log.error("[ANALITICA-SERVICE] Error processing UsuarioLoginExitoso eventId={}: {}. NACK (requeue=false).",
                    event.getEventId(), ex.getMessage(), ex);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    @RabbitHandler
    public void handleUsuarioLoginFallido(UsuarioLoginFallidoEvent event,
                                           Channel channel,
                                           @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (event == null || event.getEventId() == null) {
            channel.basicAck(deliveryTag, false);
            return;
        }

        if (isDuplicate(event.getEventId())) {
            log.info("[ANALITICA-SERVICE] Duplicate event ignored: eventId={}. Sending ACK.", event.getEventId());
            channel.basicAck(deliveryTag, false);
            return;
        }

        log.info("[ANALITICA-SERVICE] Recording analytics metric for event: type={} eventId={}",
                event.getEventType(), event.getEventId());

        try {
            String descripcion = String.format("Intento de inicio de sesion fallido: email=%s motivo='%s'",
                    event.getEmail(), event.getMotivo());

            AnaliticaEvento analiticaEvento = new AnaliticaEvento(null, "USUARIO_LOGIN_FALLIDO", descripcion);
            analiticaService.registrarEvento(analiticaEvento);

            recordProcessed(event.getEventId(), event.getEventType(), null);
            channel.basicAck(deliveryTag, false);
            log.info("[ANALITICA-SERVICE] ACK sent. Event recorded successfully: eventId={}", event.getEventId());
        } catch (Exception ex) {
            log.error("[ANALITICA-SERVICE] Error processing UsuarioLoginFallido eventId={}: {}. NACK (requeue=false).",
                    event.getEventId(), ex.getMessage(), ex);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    public boolean isEventProcessed(String eventId) {
        return isDuplicate(eventId);
    }
}
