package cl.duoc.pedidos360.pedidos.service;

import cl.duoc.pedidos360.pedidos.config.RabbitMQConfig;
import cl.duoc.pedidos360.pedidos.entity.Pedido;
import cl.duoc.pedidos360.pedidos.event.PedidoCreadoEvent;
import cl.duoc.pedidos360.pedidos.repository.PedidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class PedidoService {

    private static final Logger log = LoggerFactory.getLogger(PedidoService.class);

    private final PedidoRepository pedidoRepository;
    private final RabbitTemplate rabbitTemplate;

    public PedidoService(PedidoRepository pedidoRepository, RabbitTemplate rabbitTemplate) {
        this.pedidoRepository = pedidoRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    public List<Pedido> obtenerTodos() {
        return pedidoRepository.findAll();
    }

    public Optional<Pedido> obtenerPorId(Long id) {
        return pedidoRepository.findById(id);
    }

    public List<Pedido> obtenerPorUsuario(Long usuarioId) {
        return pedidoRepository.findByUsuarioId(usuarioId);
    }

    @Transactional
    public Pedido guardar(Pedido pedido) {
        Pedido guardado = pedidoRepository.save(pedido);
        log.info("[PEDIDOS-SERVICE] Order saved successfully with ID={}", guardado.getId());

        // Publicar evento de dominio pedido.creado a RabbitMQ
        try {
            PedidoCreadoEvent event = new PedidoCreadoEvent(
                    guardado.getId(),
                    guardado.getUsuarioId(),
                    guardado.getTotal()
            );

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_EVENTS,
                    RabbitMQConfig.ROUTING_KEY_PEDIDO_CREADO,
                    event
            );

            log.info("[PEDIDOS-SERVICE] Domain event published: eventType={} eventId={} resourceId={} usuarioId={}",
                    event.getEventType(), event.getEventId(), event.getResourceId(), event.getUsuarioId());
        } catch (Exception ex) {
            log.warn("[PEDIDOS-SERVICE] Failed to publish domain event pedido.creado for order ID={}: {}",
                    guardado.getId(), ex.getMessage());
        }

        return guardado;
    }

    @Transactional
    public Pedido actualizarEstado(Long id, String nuevoEstado) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado con ID: " + id));

        pedido.setEstado(nuevoEstado);
        Pedido actualizado = pedidoRepository.save(pedido);
        log.info("[PEDIDOS-SERVICE] Order ID={} status updated to {}", id, nuevoEstado);

        try {
            cl.duoc.pedidos360.pedidos.event.PedidoEstadoActualizadoEvent event =
                    new cl.duoc.pedidos360.pedidos.event.PedidoEstadoActualizadoEvent(
                            actualizado.getId(),
                            actualizado.getUsuarioId(),
                            actualizado.getEstado()
                    );

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_EVENTS,
                    RabbitMQConfig.ROUTING_KEY_PEDIDO_ESTADO_ACTUALIZADO,
                    event
            );

            log.info("[PEDIDOS-SERVICE] Domain event published: eventType={} eventId={} resourceId={} nuevoEstado={}",
                    event.getEventType(), event.getEventId(), event.getResourceId(), event.getNuevoEstado());
        } catch (Exception ex) {
            log.warn("[PEDIDOS-SERVICE] Failed to publish event pedido.estado.actualizado for order ID={}: {}",
                    id, ex.getMessage());
        }

        return actualizado;
    }

    @Transactional
    public Pedido asignarRepartidor(Long id, Long repartidorId, String nombreRepartidor) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado con ID: " + id));

        pedido.setRepartidorId(repartidorId);
        pedido.setEstado("EN_CAMINO");
        Pedido actualizado = pedidoRepository.save(pedido);
        log.info("[PEDIDOS-SERVICE] Order ID={} assigned to driver ID={} ({})", id, repartidorId, nombreRepartidor);

        try {
            cl.duoc.pedidos360.pedidos.event.RepartidorAsignadoEvent event =
                    new cl.duoc.pedidos360.pedidos.event.RepartidorAsignadoEvent(
                            actualizado.getId(),
                            actualizado.getUsuarioId(),
                            repartidorId,
                            nombreRepartidor
                    );

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_EVENTS,
                    RabbitMQConfig.ROUTING_KEY_REPARTIDOR_ASIGNADO,
                    event
            );

            log.info("[PEDIDOS-SERVICE] Domain event published: eventType={} eventId={} resourceId={} repartidorId={}",
                    event.getEventType(), event.getEventId(), event.getResourceId(), event.getRepartidorId());
        } catch (Exception ex) {
            log.warn("[PEDIDOS-SERVICE] Failed to publish event pedido.repartidor.asignado for order ID={}: {}",
                    id, ex.getMessage());
        }

        return actualizado;
    }

    public void eliminar(Long id) {
        pedidoRepository.deleteById(id);
    }
}
