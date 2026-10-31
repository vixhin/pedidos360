package cl.duoc.pedidos360.pedidos.service;

import cl.duoc.pedidos360.pedidos.config.RabbitMQConfig;
import cl.duoc.pedidos360.pedidos.entity.Pedido;
import cl.duoc.pedidos360.pedidos.event.PedidoCreadoEvent;
import cl.duoc.pedidos360.pedidos.event.PedidoEstadoActualizadoEvent;
import cl.duoc.pedidos360.pedidos.event.RepartidorAsignadoEvent;
import cl.duoc.pedidos360.pedidos.kafka.event.KafkaEvent;
import cl.duoc.pedidos360.pedidos.kafka.event.PedidoCreadoPayload;
import cl.duoc.pedidos360.pedidos.kafka.event.PedidoEstadoActualizadoPayload;
import cl.duoc.pedidos360.pedidos.kafka.event.RepartidorAsignadoPayload;
import cl.duoc.pedidos360.pedidos.outbox.service.OutboxService;
import cl.duoc.pedidos360.pedidos.repository.PedidoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PedidoService {

    private static final Logger log = LoggerFactory.getLogger(PedidoService.class);

    private final PedidoRepository pedidoRepository;
    private final RabbitTemplate rabbitTemplate;
    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topics.pedidos:pedidos360.pedidos.v1}")
    private String pedidosTopic;

    public PedidoService(PedidoRepository pedidoRepository,
                         RabbitTemplate rabbitTemplate,
                         OutboxService outboxService,
                         @Autowired(required = false) ObjectMapper objectMapper) {
        this.pedidoRepository = pedidoRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.outboxService = outboxService;
        this.objectMapper = (objectMapper != null) ? objectMapper : new ObjectMapper();
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

    public List<Pedido> obtenerDisponiblesOAsignados(Long repartidorId) {
        return pedidoRepository.findByRepartidorIdIsNullOrRepartidorId(repartidorId);
    }

    @Transactional
    public Pedido guardar(Pedido pedido) {
        Pedido guardado = pedidoRepository.save(pedido);
        log.info("[PEDIDOS-SERVICE] Order saved successfully with ID={}", guardado.getId());

        String eventId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();

        // 1. Transactional Outbox (Apache Kafka): persistir evento dentro de la misma transacción PostgreSQL
        try {
            PedidoCreadoPayload payload = new PedidoCreadoPayload(
                    guardado.getUsuarioId(),
                    guardado.getTotal(),
                    guardado.getEstado()
            );

            KafkaEvent<PedidoCreadoPayload> kafkaEvent = new KafkaEvent<>(
                    eventId,
                    "pedido.creado",
                    Instant.now().toString(),
                    correlationId,
                    guardado.getId(),
                    payload
            );

            String jsonPayload = objectMapper.writeValueAsString(kafkaEvent);
            outboxService.saveEvent(
                    eventId,
                    "PEDIDO",
                    guardado.getId().toString(),
                    "pedido.creado",
                    pedidosTopic,
                    guardado.getId().toString(),
                    correlationId,
                    jsonPayload
            );
        } catch (Exception ex) {
            log.error("[PEDIDOS-SERVICE] Error serializing/saving outbox event for order ID={}: {}",
                    guardado.getId(), ex.getMessage(), ex);
            throw new RuntimeException("Error en persistencia outbox de pedido", ex);
        }

        // 2. Publicar evento a RabbitMQ para mantener retrocompatibilidad (notificación, carrito, chat)
        try {
            PedidoCreadoEvent rabbitEvent = new PedidoCreadoEvent(
                    guardado.getId(),
                    guardado.getUsuarioId(),
                    guardado.getTotal()
            );
            rabbitEvent.setEventId(eventId);

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_EVENTS,
                    RabbitMQConfig.ROUTING_KEY_PEDIDO_CREADO,
                    rabbitEvent
            );

            log.info("[PEDIDOS-SERVICE] Domain event published to RabbitMQ: eventType={} eventId={} resourceId={}",
                    rabbitEvent.getEventType(), rabbitEvent.getEventId(), rabbitEvent.getResourceId());
        } catch (Exception ex) {
            log.warn("[PEDIDOS-SERVICE] Failed to publish domain event to RabbitMQ for order ID={}: {}",
                    guardado.getId(), ex.getMessage());
        }

        return guardado;
    }

    @Transactional
    public Pedido actualizarEstado(Long id, String nuevoEstado) {
        return actualizarEstado(id, nuevoEstado, null, null);
    }

    @Transactional
    public Pedido actualizarEstado(Long id, String nuevoEstado, Long callerUserId, String callerRoles) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado con ID: " + id));

        if (callerRoles != null && callerRoles.toUpperCase().contains("REPARTIDOR") && callerUserId != null) {
            if (pedido.getRepartidorId() == null || !pedido.getRepartidorId().equals(callerUserId)) {
                throw new SecurityException("Acceso denegado: El repartidor no puede modificar pedidos asignados a otros repartidores.");
            }
        }

        String anteriorEstado = pedido.getEstado();
        pedido.setEstado(nuevoEstado);
        Pedido actualizado = pedidoRepository.save(pedido);
        log.info("[PEDIDOS-SERVICE] Order ID={} status updated to {}", id, nuevoEstado);

        String eventId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();

        // 1. Transactional Outbox (Apache Kafka)
        try {
            PedidoEstadoActualizadoPayload payload = new PedidoEstadoActualizadoPayload(
                    actualizado.getUsuarioId(),
                    actualizado.getEstado(),
                    anteriorEstado
            );

            KafkaEvent<PedidoEstadoActualizadoPayload> kafkaEvent = new KafkaEvent<>(
                    eventId,
                    "pedido.estado.actualizado",
                    Instant.now().toString(),
                    correlationId,
                    actualizado.getId(),
                    payload
            );

            String jsonPayload = objectMapper.writeValueAsString(kafkaEvent);
            outboxService.saveEvent(
                    eventId,
                    "PEDIDO",
                    actualizado.getId().toString(),
                    "pedido.estado.actualizado",
                    pedidosTopic,
                    actualizado.getId().toString(),
                    correlationId,
                    jsonPayload
            );
        } catch (Exception ex) {
            log.error("[PEDIDOS-SERVICE] Error saving outbox event for status update ID={}: {}",
                    id, ex.getMessage(), ex);
            throw new RuntimeException("Error en persistencia outbox de actualizacion de estado", ex);
        }

        // 2. RabbitMQ
        try {
            PedidoEstadoActualizadoEvent rabbitEvent =
                    new PedidoEstadoActualizadoEvent(
                            actualizado.getId(),
                            actualizado.getUsuarioId(),
                            actualizado.getEstado()
                    );
            rabbitEvent.setEventId(eventId);

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_EVENTS,
                    RabbitMQConfig.ROUTING_KEY_PEDIDO_ESTADO_ACTUALIZADO,
                    rabbitEvent
            );

            log.info("[PEDIDOS-SERVICE] Domain event published to RabbitMQ: eventType={} eventId={} resourceId={} nuevoEstado={}",
                    rabbitEvent.getEventType(), rabbitEvent.getEventId(), rabbitEvent.getResourceId(), rabbitEvent.getNuevoEstado());
        } catch (Exception ex) {
            log.warn("[PEDIDOS-SERVICE] Failed to publish event pedido.estado.actualizado to RabbitMQ for order ID={}: {}",
                    id, ex.getMessage());
        }

        return actualizado;
    }

    @Transactional
    public Pedido asignarRepartidor(Long id, Long repartidorId, String nombreRepartidor) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado con ID: " + id));

        if (pedido.getRepartidorId() != null && !pedido.getRepartidorId().equals(repartidorId)) {
            throw new IllegalStateException("El pedido ya fue asignado a otro repartidor.");
        }

        pedido.setRepartidorId(repartidorId);
        pedido.setEstado("EN_CAMINO");
        Pedido actualizado = pedidoRepository.save(pedido);
        log.info("[PEDIDOS-SERVICE] Order ID={} assigned to driver ID={} ({})", id, repartidorId, nombreRepartidor);

        String eventId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();

        // 1. Transactional Outbox (Apache Kafka)
        try {
            RepartidorAsignadoPayload payload = new RepartidorAsignadoPayload(
                    actualizado.getUsuarioId(),
                    repartidorId,
                    nombreRepartidor,
                    "EN_CAMINO"
            );

            KafkaEvent<RepartidorAsignadoPayload> kafkaEvent = new KafkaEvent<>(
                    eventId,
                    "pedido.repartidor.asignado",
                    Instant.now().toString(),
                    correlationId,
                    actualizado.getId(),
                    payload
            );

            String jsonPayload = objectMapper.writeValueAsString(kafkaEvent);
            outboxService.saveEvent(
                    eventId,
                    "PEDIDO",
                    actualizado.getId().toString(),
                    "pedido.repartidor.asignado",
                    pedidosTopic,
                    actualizado.getId().toString(),
                    correlationId,
                    jsonPayload
            );
        } catch (Exception ex) {
            log.error("[PEDIDOS-SERVICE] Error saving outbox event for driver assignment ID={}: {}",
                    id, ex.getMessage(), ex);
            throw new RuntimeException("Error en persistencia outbox de asignacion de repartidor", ex);
        }

        // 2. RabbitMQ
        try {
            RepartidorAsignadoEvent rabbitEvent =
                    new RepartidorAsignadoEvent(
                            actualizado.getId(),
                            actualizado.getUsuarioId(),
                            repartidorId,
                            nombreRepartidor
                    );
            rabbitEvent.setEventId(eventId);

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE_EVENTS,
                    RabbitMQConfig.ROUTING_KEY_REPARTIDOR_ASIGNADO,
                    rabbitEvent
            );

            log.info("[PEDIDOS-SERVICE] Domain event published to RabbitMQ: eventType={} eventId={} resourceId={} repartidorId={}",
                    rabbitEvent.getEventType(), rabbitEvent.getEventId(), rabbitEvent.getResourceId(), rabbitEvent.getRepartidorId());
        } catch (Exception ex) {
            log.warn("[PEDIDOS-SERVICE] Failed to publish event pedido.repartidor.asignado to RabbitMQ for order ID={}: {}",
                    id, ex.getMessage());
        }

        return actualizado;
    }

    public void eliminar(Long id) {
        pedidoRepository.deleteById(id);
    }
}
