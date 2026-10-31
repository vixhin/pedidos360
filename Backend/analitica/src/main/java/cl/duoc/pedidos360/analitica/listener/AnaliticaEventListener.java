package cl.duoc.pedidos360.analitica.listener;

import cl.duoc.pedidos360.analitica.config.RabbitMQConfig;
import cl.duoc.pedidos360.analitica.entity.AnaliticaEvento;
import cl.duoc.pedidos360.analitica.entity.ProcessedEvent;
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
import java.time.Instant;

@Component
@RabbitListener(queues = RabbitMQConfig.QUEUE_ANALYTICS, ackMode = "MANUAL")
public class AnaliticaEventListener {

    private static final Logger log = LoggerFactory.getLogger(AnaliticaEventListener.class);

    private final AnaliticaService analiticaService;
    private final IdempotencyService idempotencyService;
    private final java.util.Set<String> processedEventIds = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public AnaliticaEventListener(AnaliticaService analiticaService) {
        this(analiticaService, null);
    }

    @Autowired
    public AnaliticaEventListener(AnaliticaService analiticaService,
                                  @Autowired(required = false) IdempotencyService idempotencyService) {
        this.analiticaService = analiticaService;
        this.idempotencyService = idempotencyService;
    }

    /**
     * Procesa un evento recibido desde RabbitMQ aplicando idempotencia transaccional persistente.
     * Si el evento ya fue procesado (por Kafka o RabbitMQ), se reconoce (ACK) sin registrar métricas repetidas.
     * Si la base de datos falla, se rechaza (NACK) para permitir DLQ y evitar pérdidas.
     */
    private void processRabbitEvent(String eventId, String eventType, String resourceId,
                                    AnaliticaEvento metrica, Channel channel, long deliveryTag) throws IOException {
        if (eventId == null || eventId.isBlank()) {
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            if (idempotencyService != null) {
                ProcessedEvent processedEvent = new ProcessedEvent(
                        eventId,
                        "RABBITMQ",
                        eventType,
                        resourceId,
                        null,
                        RabbitMQConfig.QUEUE_ANALYTICS,
                        Instant.now()
                );

                boolean processed = idempotencyService.processEventTransactional(processedEvent, metrica);

                if (!processed) {
                    log.info("[ANALITICA-SERVICE] Duplicate event ignored (already processed via Kafka/RabbitMQ): eventId={}. Sending ACK.",
                            eventId);
                } else {
                    log.info("[ANALITICA-SERVICE] Event processed atomically and metric registered: eventId={} type={}",
                            eventId, eventType);
                }
            } else {
                if (processedEventIds.contains(eventId)) {
                    log.info("[ANALITICA-SERVICE] Duplicate event ignored in standalone listener: eventId={}", eventId);
                    channel.basicAck(deliveryTag, false);
                    return;
                }
                analiticaService.registrarEvento(metrica);
                processedEventIds.add(eventId);
            }

            channel.basicAck(deliveryTag, false);

        } catch (Exception ex) {
            log.error("[ANALITICA-SERVICE] Error processing {} eventId={}: {}. Sending NACK to DLQ.",
                    eventType, eventId, ex.getMessage(), ex);
            channel.basicNack(deliveryTag, false, false);
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

        String descripcion = String.format("Pedido #%d creado por usuarioId=%d por un total de $%.0f",
                event.getResourceId(), event.getUsuarioId(), event.getTotal() != null ? event.getTotal() : 0.0);
        AnaliticaEvento metrica = new AnaliticaEvento(null, "PEDIDO_CREADO", descripcion);

        processRabbitEvent(
                event.getEventId(),
                event.getEventType(),
                event.getResourceId() != null ? event.getResourceId().toString() : null,
                metrica,
                channel,
                deliveryTag
        );
    }

    @RabbitHandler
    public void handleCarritoExpirado(CarritoExpiradoEvent event,
                                       Channel channel,
                                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (event == null || event.getEventId() == null) {
            channel.basicAck(deliveryTag, false);
            return;
        }

        String descripcion = String.format("Carrito de usuarioId=%d expirado automaticamente tras 48 hrs de inactividad",
                event.getUsuarioId());
        AnaliticaEvento metrica = new AnaliticaEvento(null, "CARRITO_EXPIRADO", descripcion);

        processRabbitEvent(
                event.getEventId(),
                event.getEventType(),
                event.getUsuarioId() != null ? event.getUsuarioId().toString() : null,
                metrica,
                channel,
                deliveryTag
        );
    }

    @RabbitHandler
    public void handlePedidoEstadoActualizado(PedidoEstadoActualizadoEvent event,
                                               Channel channel,
                                               @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (event == null || event.getEventId() == null) {
            channel.basicAck(deliveryTag, false);
            return;
        }

        String descripcion = String.format("Pedido #%d cambio de estado a '%s' (usuarioId=%d)",
                event.getResourceId(), event.getNuevoEstado(), event.getUsuarioId());
        AnaliticaEvento metrica = new AnaliticaEvento(null, "PEDIDO_ESTADO_ACTUALIZADO", descripcion);

        processRabbitEvent(
                event.getEventId(),
                event.getEventType(),
                event.getResourceId() != null ? event.getResourceId().toString() : null,
                metrica,
                channel,
                deliveryTag
        );
    }

    @RabbitHandler
    public void handleRepartidorAsignado(RepartidorAsignadoEvent event,
                                          Channel channel,
                                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (event == null || event.getEventId() == null) {
            channel.basicAck(deliveryTag, false);
            return;
        }

        String descripcion = String.format("Repartidor %s (ID=%d) asignado a pedido #%d",
                event.getNombreRepartidor() != null ? event.getNombreRepartidor() : "desconocido",
                event.getRepartidorId(), event.getResourceId());
        AnaliticaEvento metrica = new AnaliticaEvento(null, "REPARTIDOR_ASIGNADO", descripcion);

        processRabbitEvent(
                event.getEventId(),
                event.getEventType(),
                event.getResourceId() != null ? event.getResourceId().toString() : null,
                metrica,
                channel,
                deliveryTag
        );
    }

    @RabbitHandler
    public void handleUsuarioLoginExitoso(UsuarioLoginExitosoEvent event,
                                           Channel channel,
                                           @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (event == null || event.getEventId() == null) {
            channel.basicAck(deliveryTag, false);
            return;
        }

        String descripcion = String.format("Inicio de sesion exitoso: usuarioId=%d email=%s rol=%s",
                event.getUsuarioId(), event.getEmail(), event.getRol());
        AnaliticaEvento metrica = new AnaliticaEvento(null, "USUARIO_LOGIN_EXITOSO", descripcion);

        processRabbitEvent(
                event.getEventId(),
                event.getEventType(),
                event.getUsuarioId() != null ? event.getUsuarioId().toString() : null,
                metrica,
                channel,
                deliveryTag
        );
    }

    @RabbitHandler
    public void handleUsuarioLoginFallido(UsuarioLoginFallidoEvent event,
                                           Channel channel,
                                           @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        if (event == null || event.getEventId() == null) {
            channel.basicAck(deliveryTag, false);
            return;
        }

        String descripcion = String.format("Intento de inicio de sesion fallido: email=%s motivo='%s'",
                event.getEmail(), event.getMotivo());
        AnaliticaEvento metrica = new AnaliticaEvento(null, "USUARIO_LOGIN_FALLIDO", descripcion);

        processRabbitEvent(
                event.getEventId(),
                event.getEventType(),
                null,
                metrica,
                channel,
                deliveryTag
        );
    }

    public boolean isEventProcessed(String eventId) {
        if (eventId == null) return false;
        if (idempotencyService != null) {
            return idempotencyService.isAlreadyProcessed(eventId);
        }
        return processedEventIds.contains(eventId);
    }
}
