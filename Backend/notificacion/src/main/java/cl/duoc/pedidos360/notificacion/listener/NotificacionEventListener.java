package cl.duoc.pedidos360.notificacion.listener;

import cl.duoc.pedidos360.notificacion.config.RabbitMQConfig;
import cl.duoc.pedidos360.notificacion.entity.Notificacion;
import cl.duoc.pedidos360.notificacion.event.CarritoExpiradoEvent;
import cl.duoc.pedidos360.notificacion.event.PedidoCreadoEvent;
import cl.duoc.pedidos360.notificacion.event.PasswordResetSolicitadoEvent;
import cl.duoc.pedidos360.notificacion.event.PedidoEstadoActualizadoEvent;
import cl.duoc.pedidos360.notificacion.event.RepartidorAsignadoEvent;
import cl.duoc.pedidos360.notificacion.service.NotificacionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RabbitListener(queues = RabbitMQConfig.QUEUE_NOTIFICATIONS)
public class NotificacionEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificacionEventListener.class);

    private final NotificacionService notificacionService;
    private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();

    public NotificacionEventListener(NotificacionService notificacionService) {
        this.notificacionService = notificacionService;
    }

    @RabbitHandler
    public void handlePedidoCreado(PedidoCreadoEvent event) {
        if (event == null || event.getEventId() == null) return;

        if (processedEventIds.contains(event.getEventId())) {
            log.info("[NOTIFICACION-SERVICE] Duplicate event ignored: eventId={}", event.getEventId());
            return;
        }

        log.info("[NOTIFICACION-SERVICE] Processing event: type={} eventId={} resourceId={}",
                event.getEventType(), event.getEventId(), event.getResourceId());

        String mensaje = String.format("¡Tu pedido #%d por $%.0f ha sido creado exitosamente!",
                event.getResourceId(), event.getTotal() != null ? event.getTotal() : 0.0);

        Notificacion notif = new Notificacion(null, event.getUsuarioId(), mensaje, "SYSTEM");
        notificacionService.enviarNotificacion(notif);

        processedEventIds.add(event.getEventId());
        log.info("[NOTIFICACION-SERVICE] Notification created for usuarioId={} eventId={}",
                event.getUsuarioId(), event.getEventId());
    }

    @RabbitHandler
    public void handleCarritoExpirado(CarritoExpiradoEvent event) {
        if (event == null || event.getEventId() == null) return;

        if (processedEventIds.contains(event.getEventId())) {
            log.info("[NOTIFICACION-SERVICE] Duplicate event ignored: eventId={}", event.getEventId());
            return;
        }

        log.info("[NOTIFICACION-SERVICE] Processing event: type={} eventId={} usuarioId={}",
                event.getEventType(), event.getEventId(), event.getUsuarioId());

        String mensaje = "Tu carrito de compras ha sido vaciado automáticamente debido a 48 horas de inactividad.";

        Notificacion notif = new Notificacion(null, event.getUsuarioId(), mensaje, "SYSTEM");
        notificacionService.enviarNotificacion(notif);

        processedEventIds.add(event.getEventId());
        log.info("[NOTIFICACION-SERVICE] Cart expiration notification created for usuarioId={} eventId={}",
                event.getUsuarioId(), event.getEventId());
    }


    @RabbitHandler
    public void handlePedidoEstadoActualizado(PedidoEstadoActualizadoEvent event) {
        if (event == null || event.getEventId() == null) return;

        if (processedEventIds.contains(event.getEventId())) {
            log.info("[NOTIFICACION-SERVICE] Duplicate event ignored: eventId={}", event.getEventId());
            return;
        }

        log.info("[NOTIFICACION-SERVICE] Processing event: type={} eventId={} resourceId={} nuevoEstado={}",
                event.getEventType(), event.getEventId(), event.getResourceId(), event.getNuevoEstado());

        String mensaje = String.format("Tu pedido #%d ha cambiado de estado a: %s",
                event.getResourceId(), event.getNuevoEstado());

        Notificacion notif = new Notificacion(null, event.getUsuarioId(), mensaje, "SYSTEM");
        notificacionService.enviarNotificacion(notif);

        processedEventIds.add(event.getEventId());
        log.info("[NOTIFICACION-SERVICE] Order status update notification created for usuarioId={} eventId={}",
                event.getUsuarioId(), event.getEventId());
    }

    @RabbitHandler
    public void handleRepartidorAsignado(RepartidorAsignadoEvent event) {
        if (event == null || event.getEventId() == null) return;

        if (processedEventIds.contains(event.getEventId())) {
            log.info("[NOTIFICACION-SERVICE] Duplicate event ignored: eventId={}", event.getEventId());
            return;
        }

        log.info("[NOTIFICACION-SERVICE] Processing event: type={} eventId={} resourceId={} repartidor={}",
                event.getEventType(), event.getEventId(), event.getResourceId(), event.getNombreRepartidor());

        String mensaje = String.format("El repartidor %s ha sido asignado a tu pedido #%d y va en camino.",
                event.getNombreRepartidor() != null ? event.getNombreRepartidor() : "asignado", event.getResourceId());

        Notificacion notif = new Notificacion(null, event.getUsuarioId(), mensaje, "SYSTEM");
        notificacionService.enviarNotificacion(notif);

        processedEventIds.add(event.getEventId());
        log.info("[NOTIFICACION-SERVICE] Driver assigned notification created for usuarioId={} eventId={}",
                event.getUsuarioId(), event.getEventId());
    }


    @RabbitHandler
    public void handlePasswordResetSolicitado(PasswordResetSolicitadoEvent event) {
        if (event == null || event.getEventId() == null) return;

        if (processedEventIds.contains(event.getEventId())) {
            log.info("[NOTIFICACION-SERVICE] Duplicate event ignored: eventId={}", event.getEventId());
            return;
        }

        log.info("[NOTIFICACION-SERVICE] Processing event: type={} eventId={} email={}",
                event.getEventType(), event.getEventId(), event.getEmail());

        String mensaje = String.format("Has solicitado restablecer tu contraseña. Usa el siguiente token: %s",
                event.getResetToken());

        Notificacion notif = new Notificacion(null, event.getUsuarioId(), mensaje, "SYSTEM");
        notificacionService.enviarNotificacion(notif);

        processedEventIds.add(event.getEventId());
        log.info("[NOTIFICACION-SERVICE] Password reset notification created for email={} eventId={}",
                event.getEmail(), event.getEventId());
    }

    public boolean isEventProcessed(String eventId) {
        return processedEventIds.contains(eventId);
    }
}
