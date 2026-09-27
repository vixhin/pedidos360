package cl.duoc.pedidos360.carrito.service;

import cl.duoc.pedidos360.carrito.config.RabbitMQConfig;
import cl.duoc.pedidos360.carrito.entity.CarritoItem;
import cl.duoc.pedidos360.carrito.event.CarritoExpiradoEvent;
import cl.duoc.pedidos360.carrito.repository.CarritoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CarritoExpiracionService {

    private static final Logger log = LoggerFactory.getLogger(CarritoExpiracionService.class);

    private final CarritoRepository carritoRepository;
    private final CarritoService carritoService;
    private final RabbitTemplate rabbitTemplate;

    public CarritoExpiracionService(CarritoRepository carritoRepository,
                                    CarritoService carritoService,
                                    RabbitTemplate rabbitTemplate) {
        this.carritoRepository = carritoRepository;
        this.carritoService = carritoService;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Revisa carritos abandonados (sin actividad durante 48 horas).
     * Se ejecuta cada hora (o mediante invocación explícita).
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void procesarCarritosExpirados() {
        LocalDateTime limite = LocalDateTime.now().minusHours(48);
        List<CarritoItem> itemsInactivos = carritoRepository.findByUltimaActividadBefore(limite);

        if (itemsInactivos.isEmpty()) {
            return;
        }

        Set<Long> usuariosExpirados = itemsInactivos.stream()
                .map(CarritoItem::getUsuarioId)
                .collect(Collectors.toSet());

        log.info("[CARRITO-SERVICE] Found {} abandoned cart items for {} users inactive since {}",
                itemsInactivos.size(), usuariosExpirados.size(), limite);

        for (Long usuarioId : usuariosExpirados) {
            // Verificar actividad más reciente del usuario
            List<CarritoItem> itemsUsuario = carritoRepository.findByUsuarioId(usuarioId);
            boolean sigueAbandonado = itemsUsuario.stream()
                    .allMatch(item -> item.getUltimaActividad() == null || item.getUltimaActividad().isBefore(limite) || item.getUltimaActividad().isEqual(limite));

            if (sigueAbandonado) {
                carritoService.vaciarCarrito(usuarioId);
                log.info("[CARRITO-SERVICE] Expired abandoned cart cleared for usuarioId={}", usuarioId);

                // Publicar evento de expiración a RabbitMQ
                try {
                    CarritoExpiradoEvent event = new CarritoExpiradoEvent(usuarioId);
                    rabbitTemplate.convertAndSend(
                            RabbitMQConfig.EXCHANGE_EVENTS,
                            RabbitMQConfig.ROUTING_KEY_CARRITO_EXPIRADO,
                            event
                    );
                    log.info("[CARRITO-SERVICE] Published event carrito.expirado: eventId={} usuarioId={}",
                            event.getEventId(), usuarioId);
                } catch (Exception ex) {
                    log.warn("[CARRITO-SERVICE] Failed to publish event carrito.expirado for usuarioId={}: {}",
                            usuarioId, ex.getMessage());
                }
            } else {
                log.info("[CARRITO-SERVICE] Skip cart expiration for usuarioId={}: user performed recent activity", usuarioId);
            }
        }
    }
}
