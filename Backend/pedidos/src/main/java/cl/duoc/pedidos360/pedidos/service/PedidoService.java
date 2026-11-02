package cl.duoc.pedidos360.pedidos.service;

import cl.duoc.pedidos360.pedidos.config.RabbitMQConfig;
import cl.duoc.pedidos360.pedidos.entity.Pedido;
import cl.duoc.pedidos360.pedidos.entity.PedidoItem;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
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

    @Value("${bff.internal-key:${BFF_INTERNAL_KEY:}}")
    private String bffInternalKey;

    public PedidoService(PedidoRepository pedidoRepository,
                         RabbitTemplate rabbitTemplate,
                         @Autowired(required = false) OutboxService outboxService,
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
        // Idempotencia: si ya existe una orden con esta clave, se devuelve directamente sin duplicar
        if (pedido.getIdempotencyKey() != null && !pedido.getIdempotencyKey().isBlank()) {
            Optional<Pedido> existente = pedidoRepository.findByIdempotencyKey(pedido.getIdempotencyKey().trim());
            if (existente.isPresent()) {
                log.info("[PEDIDOS-SERVICE] Pedido idempotente ya existente retornado: ID={}, IdempotencyKey='{}'",
                        existente.get().getId(), pedido.getIdempotencyKey());
                return existente.get();
            }
        }

        if (pedido.getFechaCreacion() == null) {
            pedido.setFechaCreacion(LocalDateTime.now());
        }
        if (pedido.getEstado() == null || pedido.getEstado().isBlank()) {
            pedido.setEstado("PENDIENTE");
        }
        if (pedido.getItems() != null) {
            for (PedidoItem item : pedido.getItems()) {
                item.setPedido(pedido);
            }
        }
        if (pedido.getTotal() == null || pedido.getTotal() <= 0) {
            double subtotal = 0.0;
            if (pedido.getItems() != null) {
                for (PedidoItem item : pedido.getItems()) {
                    if (item.getPrecioUnitario() != null && item.getCantidad() != null) {
                        subtotal += item.getPrecioUnitario() * item.getCantidad();
                    }
                }
            }
            pedido.setSubtotal(subtotal);
            double envio = pedido.getCostoEnvio() != null ? pedido.getCostoEnvio() : 0.0;
            double tarifa = pedido.getTarifaServicio() != null ? pedido.getTarifaServicio() : 1500.0;
            pedido.setTotal(subtotal + envio + tarifa);
        }

        Pedido guardado = pedidoRepository.save(pedido);
        log.info("[PEDIDOS-SERVICE] Order saved successfully with ID={}", guardado.getId());

        // Descontar stock directamente en el microservicio de productos
        if (guardado.getItems() != null && !guardado.getItems().isEmpty()) {
            descontarStockDeProductos(guardado.getItems());
        }

        String eventId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();

        // 1. Transactional Outbox (Apache Kafka): persistir evento dentro de la misma transacción PostgreSQL
        if (outboxService != null) {
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
        }

        // 2. Publicación a RabbitMQ posterior al commit de PostgreSQL
        PedidoCreadoEvent rabbitEvent = new PedidoCreadoEvent(
                guardado.getId(),
                guardado.getUsuarioId(),
                guardado.getTotal()
        );
        rabbitEvent.setEventId(eventId);

        publishRabbitEventAfterCommit(
                RabbitMQConfig.EXCHANGE_EVENTS,
                RabbitMQConfig.ROUTING_KEY_PEDIDO_CREADO,
                rabbitEvent,
                rabbitEvent.getEventType(),
                eventId,
                guardado.getId()
        );

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

        if (callerRoles != null && !callerRoles.isBlank()) {
            boolean esAdmin = callerRoles.contains("ADMIN");
            boolean esRepartidor = callerRoles.contains("REPARTIDOR");
            if (esRepartidor && !esAdmin) {
                if (pedido.getRepartidorId() != null && callerUserId != null && !pedido.getRepartidorId().equals(callerUserId)) {
                    throw new SecurityException("El repartidor no tiene permiso para actualizar este pedido asignado a otro repartidor.");
                }
            }
        }

        String estadoAnterior = pedido.getEstado();
        pedido.setEstado(nuevoEstado);
        Pedido actualizado = pedidoRepository.save(pedido);
        log.info("[PEDIDOS-SERVICE] Order ID={} state updated: {} -> {}", id, estadoAnterior, nuevoEstado);

        String eventId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();

        // 1. Transactional Outbox (Apache Kafka)
        if (outboxService != null) {
            try {
                PedidoEstadoActualizadoPayload payload = new PedidoEstadoActualizadoPayload(
                        actualizado.getId(),
                        estadoAnterior,
                        nuevoEstado
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
        }

        // 2. RabbitMQ posterior al commit
        PedidoEstadoActualizadoEvent rabbitEvent =
                new PedidoEstadoActualizadoEvent(
                        actualizado.getId(),
                        actualizado.getUsuarioId(),
                        actualizado.getEstado()
                );
        rabbitEvent.setEventId(eventId);

        publishRabbitEventAfterCommit(
                RabbitMQConfig.EXCHANGE_EVENTS,
                RabbitMQConfig.ROUTING_KEY_PEDIDO_ESTADO_ACTUALIZADO,
                rabbitEvent,
                rabbitEvent.getEventType(),
                eventId,
                actualizado.getId()
        );

        return actualizado;
    }

    @Transactional
    public Pedido asignarRepartidor(Long id, Long repartidorId, String nombreRepartidor) {
        return asignarRepartidor(id, repartidorId, nombreRepartidor, null);
    }

    @Transactional
    public Pedido asignarRepartidor(Long id, Long repartidorId, String nombreRepartidor, String telefonoRepartidor) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Pedido no encontrado con ID: " + id));

        if (pedido.getRepartidorId() != null && !pedido.getRepartidorId().equals(repartidorId)) {
            throw new IllegalStateException("El pedido ya fue asignado a otro repartidor.");
        }

        pedido.setRepartidorId(repartidorId);
        if (nombreRepartidor != null) {
            pedido.setRepartidorNombre(nombreRepartidor);
        }
        if (telefonoRepartidor != null) {
            pedido.setRepartidorTelefono(telefonoRepartidor);
        }
        pedido.setEstado("EN_CAMINO");
        Pedido actualizado = pedidoRepository.save(pedido);
        log.info("[PEDIDOS-SERVICE] Order ID={} assigned to driver ID={} ({})", id, repartidorId, nombreRepartidor);

        String eventId = UUID.randomUUID().toString();
        String correlationId = UUID.randomUUID().toString();

        // 1. Transactional Outbox (Apache Kafka)
        if (outboxService != null) {
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
                throw new RuntimeException("Error en persistencia outbox de repartidor asignado", ex);
            }
        }

        // 2. RabbitMQ posterior al commit
        RepartidorAsignadoEvent rabbitEvent =
                new RepartidorAsignadoEvent(
                        actualizado.getId(),
                        actualizado.getUsuarioId(),
                        repartidorId,
                        nombreRepartidor
                );
        rabbitEvent.setEventId(eventId);

        publishRabbitEventAfterCommit(
                RabbitMQConfig.EXCHANGE_EVENTS,
                RabbitMQConfig.ROUTING_KEY_REPARTIDOR_ASIGNADO,
                rabbitEvent,
                rabbitEvent.getEventType(),
                eventId,
                actualizado.getId()
        );

        return actualizado;
    }

    /**
     * Publica el evento a RabbitMQ de forma confiable únicamente tras el commit exitoso de PostgreSQL.
     * Si la transacción de base de datos hace rollback, el evento no se emite, evitando eventos fantasma.
     */
    private void publishRabbitEventAfterCommit(String exchange, String routingKey, Object event,
                                                String eventType, String eventId, Long resourceId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        rabbitTemplate.convertAndSend(exchange, routingKey, event);
                        log.info("[PEDIDOS-SERVICE] Domain event published to RabbitMQ (after-commit): eventType={} eventId={} resourceId={}",
                                eventType, eventId, resourceId);
                    } catch (Exception ex) {
                        log.error("[PEDIDOS-SERVICE] Failed to publish domain event to RabbitMQ after commit for order ID={}: {}",
                                resourceId, ex.getMessage());
                    }
                }
            });
        } else {
            try {
                rabbitTemplate.convertAndSend(exchange, routingKey, event);
                log.info("[PEDIDOS-SERVICE] Domain event published to RabbitMQ (immediate): eventType={} eventId={} resourceId={}",
                        eventType, eventId, resourceId);
            } catch (Exception ex) {
                log.error("[PEDIDOS-SERVICE] Failed to publish domain event to RabbitMQ for order ID={}: {}",
                        resourceId, ex.getMessage());
            }
        }
    }

    private void descontarStockDeProductos(List<PedidoItem> items) {
        String productosUrl = System.getenv("PRODUCTOS_SERVICE_URL");
        if (productosUrl == null || productosUrl.isBlank()) {
            productosUrl = "http://localhost:8085";
        }
        RestTemplate rt = new RestTemplate();
        for (PedidoItem item : items) {
            if (item.getProductoId() != null) {
                try {
                    int qty = (item.getCantidad() != null && item.getCantidad() > 0) ? item.getCantidad() : 1;
                    String url = productosUrl + "/api/productos/" + item.getProductoId() + "/stock/descontar?cantidad=" + qty;
                    HttpHeaders headers = new HttpHeaders();
                    if (bffInternalKey != null && !bffInternalKey.isBlank()) {
                        headers.set("X-Internal-Service-Key", bffInternalKey);
                    }
                    HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
                    rt.exchange(url, HttpMethod.PUT, requestEntity, Void.class);
                    log.info("[PEDIDOS-SERVICE] Successfully discounted stock for product ID={} qty={}", item.getProductoId(), qty);
                } catch (Exception e) {
                    log.warn("[PEDIDOS-SERVICE] Could not deduct stock via REST for product ID={}: {}", item.getProductoId(), e.getMessage());
                }
            }
        }
    }

    public void eliminar(Long id) {
        pedidoRepository.deleteById(id);
    }
}
