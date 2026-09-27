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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RabbitListener(queues = RabbitMQConfig.QUEUE_ANALYTICS)
public class AnaliticaEventListener {

    private static final Logger log = LoggerFactory.getLogger(AnaliticaEventListener.class);

    private final AnaliticaService analiticaService;
    private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();

    public AnaliticaEventListener(AnaliticaService analiticaService) {
        this.analiticaService = analiticaService;
    }

    @RabbitHandler
    public void handlePedidoCreado(PedidoCreadoEvent event) {
        if (event == null || event.getEventId() == null) return;

        if (processedEventIds.contains(event.getEventId())) {
            log.info("[ANALITICA-SERVICE] Duplicate event ignored: eventId={}", event.getEventId());
            return;
        }

        log.info("[ANALITICA-SERVICE] Recording analytics metric for event: type={} eventId={}",
                event.getEventType(), event.getEventId());

        String descripcion = String.format("Pedido #%d creado por usuarioId=%d por un total de $%.0f",
                event.getResourceId(), event.getUsuarioId(), event.getTotal() != null ? event.getTotal() : 0.0);

        AnaliticaEvento analiticaEvento = new AnaliticaEvento(null, "PEDIDO_CREADO", descripcion);
        analiticaService.registrarEvento(analiticaEvento);

        processedEventIds.add(event.getEventId());
        log.info("[ANALITICA-SERVICE] Event recorded successfully: eventId={}", event.getEventId());
    }

    @RabbitHandler
    public void handleCarritoExpirado(CarritoExpiradoEvent event) {
        if (event == null || event.getEventId() == null) return;

        if (processedEventIds.contains(event.getEventId())) {
            log.info("[ANALITICA-SERVICE] Duplicate event ignored: eventId={}", event.getEventId());
            return;
        }

        log.info("[ANALITICA-SERVICE] Recording analytics metric for event: type={} eventId={}",
                event.getEventType(), event.getEventId());

        String descripcion = String.format("Carrito de usuarioId=%d expirado automaticamente tras 48 hrs de inactividad",
                event.getUsuarioId());

        AnaliticaEvento analiticaEvento = new AnaliticaEvento(null, "CARRITO_EXPIRADO", descripcion);
        analiticaService.registrarEvento(analiticaEvento);

        processedEventIds.add(event.getEventId());
        log.info("[ANALITICA-SERVICE] Event recorded successfully: eventId={}", event.getEventId());
    }


    @RabbitHandler
    public void handlePedidoEstadoActualizado(PedidoEstadoActualizadoEvent event) {
        if (event == null || event.getEventId() == null) return;

        if (processedEventIds.contains(event.getEventId())) {
            log.info("[ANALITICA-SERVICE] Duplicate event ignored: eventId={}", event.getEventId());
            return;
        }

        log.info("[ANALITICA-SERVICE] Recording analytics metric for event: type={} eventId={}",
                event.getEventType(), event.getEventId());

        String descripcion = String.format("Pedido #%d cambio de estado a '%s' (usuarioId=%d)",
                event.getResourceId(), event.getNuevoEstado(), event.getUsuarioId());

        AnaliticaEvento analiticaEvento = new AnaliticaEvento(null, "PEDIDO_ESTADO_ACTUALIZADO", descripcion);
        analiticaService.registrarEvento(analiticaEvento);

        processedEventIds.add(event.getEventId());
        log.info("[ANALITICA-SERVICE] Event recorded successfully: eventId={}", event.getEventId());
    }

    @RabbitHandler
    public void handleRepartidorAsignado(RepartidorAsignadoEvent event) {
        if (event == null || event.getEventId() == null) return;

        if (processedEventIds.contains(event.getEventId())) {
            log.info("[ANALITICA-SERVICE] Duplicate event ignored: eventId={}", event.getEventId());
            return;
        }

        log.info("[ANALITICA-SERVICE] Recording analytics metric for event: type={} eventId={}",
                event.getEventType(), event.getEventId());

        String descripcion = String.format("Repartidor %s (ID=%d) asignado a pedido #%d",
                event.getNombreRepartidor() != null ? event.getNombreRepartidor() : "desconocido",
                event.getRepartidorId(), event.getResourceId());

        AnaliticaEvento analiticaEvento = new AnaliticaEvento(null, "REPARTIDOR_ASIGNADO", descripcion);
        analiticaService.registrarEvento(analiticaEvento);

        processedEventIds.add(event.getEventId());
        log.info("[ANALITICA-SERVICE] Event recorded successfully: eventId={}", event.getEventId());
    }


    @RabbitHandler
    public void handleUsuarioLoginExitoso(UsuarioLoginExitosoEvent event) {
        if (event == null || event.getEventId() == null) return;

        if (processedEventIds.contains(event.getEventId())) {
            log.info("[ANALITICA-SERVICE] Duplicate event ignored: eventId={}", event.getEventId());
            return;
        }

        log.info("[ANALITICA-SERVICE] Recording analytics metric for event: type={} eventId={}",
                event.getEventType(), event.getEventId());

        String descripcion = String.format("Inicio de sesion exitoso: usuarioId=%d email=%s rol=%s",
                event.getUsuarioId(), event.getEmail(), event.getRol());

        AnaliticaEvento analiticaEvento = new AnaliticaEvento(null, "USUARIO_LOGIN_EXITOSO", descripcion);
        analiticaService.registrarEvento(analiticaEvento);

        processedEventIds.add(event.getEventId());
        log.info("[ANALITICA-SERVICE] Event recorded successfully: eventId={}", event.getEventId());
    }

    @RabbitHandler
    public void handleUsuarioLoginFallido(UsuarioLoginFallidoEvent event) {
        if (event == null || event.getEventId() == null) return;

        if (processedEventIds.contains(event.getEventId())) {
            log.info("[ANALITICA-SERVICE] Duplicate event ignored: eventId={}", event.getEventId());
            return;
        }

        log.info("[ANALITICA-SERVICE] Recording analytics metric for event: type={} eventId={}",
                event.getEventType(), event.getEventId());

        String descripcion = String.format("Intento de inicio de sesion fallido: email=%s motivo='%s'",
                event.getEmail(), event.getMotivo());

        AnaliticaEvento analiticaEvento = new AnaliticaEvento(null, "USUARIO_LOGIN_FALLIDO", descripcion);
        analiticaService.registrarEvento(analiticaEvento);

        processedEventIds.add(event.getEventId());
        log.info("[ANALITICA-SERVICE] Event recorded successfully: eventId={}", event.getEventId());
    }

    public boolean isEventProcessed(String eventId) {
        return processedEventIds.contains(eventId);
    }
}
