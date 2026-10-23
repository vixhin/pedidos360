package cl.duoc.pedidos360.usuario.service;

import cl.duoc.pedidos360.usuario.dto.*;
import cl.duoc.pedidos360.usuario.entity.*;
import cl.duoc.pedidos360.usuario.exception.ProtectedResourceException;
import cl.duoc.pedidos360.usuario.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RabbitAdminService {

    private static final Logger log = LoggerFactory.getLogger(RabbitAdminService.class);

    private final RabbitExchangeRepository exchangeRepository;
    private final RabbitQueueRepository queueRepository;
    private final RabbitBindingRepository bindingRepository;
    private final RabbitAdmin rabbitAdmin;

    public RabbitAdminService(RabbitExchangeRepository exchangeRepository,
                              RabbitQueueRepository queueRepository,
                              RabbitBindingRepository bindingRepository,
                              RabbitAdmin rabbitAdmin) {
        this.exchangeRepository = exchangeRepository;
        this.queueRepository = queueRepository;
        this.bindingRepository = bindingRepository;
        this.rabbitAdmin = rabbitAdmin;
    }

    // =========================================================================
    // EXCHANGES
    // =========================================================================

    @Transactional(readOnly = true)
    public List<RabbitExchangeResponseDTO> listExchanges() {
        return exchangeRepository.findAll().stream()
                .map(this::toExchangeResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RabbitExchangeResponseDTO getExchangeById(Long id) {
        RabbitExchange exchange = exchangeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Exchange no encontrado con ID: " + id));
        return toExchangeResponse(exchange);
    }

    @Transactional
    public RabbitExchangeResponseDTO createExchange(RabbitExchangeDTO dto) {
        if (exchangeRepository.existsByName(dto.getName())) {
            throw new IllegalArgumentException("Ya existe un exchange con el nombre: " + dto.getName());
        }

        RabbitExchange exchange = new RabbitExchange();
        exchange.setName(dto.getName());
        exchange.setType(dto.getType().toUpperCase());
        exchange.setDurable(dto.getDurable() != null ? dto.getDurable() : true);
        exchange.setAutoDelete(dto.getAutoDelete() != null ? dto.getAutoDelete() : false);
        exchange.setInternal(dto.getInternal() != null ? dto.getInternal() : false);
        exchange.setProtectedResource(dto.getProtectedResource() != null ? dto.getProtectedResource() : false);
        exchange.setActive(dto.getActive() != null ? dto.getActive() : true);

        RabbitExchange saved = exchangeRepository.save(exchange);

        try {
            if (Boolean.TRUE.equals(saved.getActive())) {
                declareExchangeInRabbitMQ(saved);
            }
        } catch (Exception ex) {
            log.error("[RABBIT-ADMIN] Error declarando exchange en RabbitMQ broker: {}", ex.getMessage());
            exchangeRepository.delete(saved);
            throw new IllegalStateException("Error al declarar exchange en RabbitMQ: " + ex.getMessage(), ex);
        }

        log.info("[RABBIT-ADMIN] Exchange creado exitosamente: name={} type={}", saved.getName(), saved.getType());
        return toExchangeResponse(saved);
    }

    @Transactional
    public RabbitExchangeResponseDTO updateExchange(Long id, RabbitExchangeDTO dto) {
        RabbitExchange exchange = exchangeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Exchange no encontrado con ID: " + id));

        if (Boolean.TRUE.equals(exchange.getProtectedResource()) && Boolean.FALSE.equals(dto.getActive())) {
            throw new ProtectedResourceException("No se puede desactivar el exchange protegido: " + exchange.getName());
        }

        if (dto.getType() != null) {
            exchange.setType(dto.getType().toUpperCase());
        }
        if (dto.getDurable() != null) exchange.setDurable(dto.getDurable());
        if (dto.getAutoDelete() != null) exchange.setAutoDelete(dto.getAutoDelete());
        if (dto.getInternal() != null) exchange.setInternal(dto.getInternal());
        if (dto.getActive() != null) exchange.setActive(dto.getActive());

        RabbitExchange updated = exchangeRepository.save(exchange);
        if (Boolean.TRUE.equals(updated.getActive())) {
            declareExchangeInRabbitMQ(updated);
        }

        log.info("[RABBIT-ADMIN] Exchange actualizado: ID={} name={}", updated.getId(), updated.getName());
        return toExchangeResponse(updated);
    }

    @Transactional
    public void deleteExchange(Long id) {
        RabbitExchange exchange = exchangeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Exchange no encontrado con ID: " + id));

        if (Boolean.TRUE.equals(exchange.getProtectedResource())) {
            log.warn("[RABBIT-ADMIN] Intento de eliminar exchange protegido: {}", exchange.getName());
            throw new ProtectedResourceException("El exchange '" + exchange.getName() + "' es un recurso protegido y no puede ser eliminado");
        }

        try {
            rabbitAdmin.deleteExchange(exchange.getName());
        } catch (Exception ex) {
            log.warn("[RABBIT-ADMIN] Advertencia al eliminar exchange en broker: {}", ex.getMessage());
        }

        exchangeRepository.delete(exchange);
        log.info("[RABBIT-ADMIN] Exchange eliminado de DB y RabbitMQ: name={}", exchange.getName());
    }

    // =========================================================================
    // QUEUES
    // =========================================================================

    @Transactional(readOnly = true)
    public List<RabbitQueueResponseDTO> listQueues() {
        return queueRepository.findAll().stream()
                .map(this::toQueueResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RabbitQueueResponseDTO getQueueById(Long id) {
        RabbitQueue queue = queueRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Queue no encontrada con ID: " + id));
        return toQueueResponse(queue);
    }

    @Transactional
    public RabbitQueueResponseDTO createQueue(RabbitQueueDTO dto) {
        if (queueRepository.existsByName(dto.getName())) {
            throw new IllegalArgumentException("Ya existe una queue con el nombre: " + dto.getName());
        }

        RabbitExchange dlx = null;
        if (dto.getDeadLetterExchangeId() != null) {
            dlx = exchangeRepository.findById(dto.getDeadLetterExchangeId())
                    .orElseThrow(() -> new IllegalArgumentException("Dead letter exchange no encontrado con ID: " + dto.getDeadLetterExchangeId()));
        }

        RabbitQueue queue = new RabbitQueue();
        queue.setName(dto.getName());
        queue.setDurable(dto.getDurable() != null ? dto.getDurable() : true);
        queue.setExclusive(dto.getExclusive() != null ? dto.getExclusive() : false);
        queue.setAutoDelete(dto.getAutoDelete() != null ? dto.getAutoDelete() : false);
        queue.setDeadLetterExchange(dlx);
        queue.setDeadLetterRoutingKey(dto.getDeadLetterRoutingKey());
        queue.setProtectedResource(dto.getProtectedResource() != null ? dto.getProtectedResource() : false);
        queue.setActive(dto.getActive() != null ? dto.getActive() : true);

        RabbitQueue saved = queueRepository.save(queue);

        try {
            if (Boolean.TRUE.equals(saved.getActive())) {
                declareQueueInRabbitMQ(saved);
            }
        } catch (Exception ex) {
            log.error("[RABBIT-ADMIN] Error declarando queue en RabbitMQ broker: {}", ex.getMessage());
            queueRepository.delete(saved);
            throw new IllegalStateException("Error al declarar queue en RabbitMQ: " + ex.getMessage(), ex);
        }

        log.info("[RABBIT-ADMIN] Queue creada exitosamente: name={}", saved.getName());
        return toQueueResponse(saved);
    }

    @Transactional
    public RabbitQueueResponseDTO updateQueue(Long id, RabbitQueueDTO dto) {
        RabbitQueue queue = queueRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Queue no encontrada con ID: " + id));

        if (Boolean.TRUE.equals(queue.getProtectedResource()) && Boolean.FALSE.equals(dto.getActive())) {
            throw new ProtectedResourceException("No se puede desactivar la queue protegida: " + queue.getName());
        }

        if (dto.getDeadLetterExchangeId() != null) {
            RabbitExchange dlx = exchangeRepository.findById(dto.getDeadLetterExchangeId())
                    .orElseThrow(() -> new IllegalArgumentException("Dead letter exchange no encontrado con ID: " + dto.getDeadLetterExchangeId()));
            queue.setDeadLetterExchange(dlx);
        }
        if (dto.getDeadLetterRoutingKey() != null) {
            queue.setDeadLetterRoutingKey(dto.getDeadLetterRoutingKey());
        }
        if (dto.getDurable() != null) queue.setDurable(dto.getDurable());
        if (dto.getExclusive() != null) queue.setExclusive(dto.getExclusive());
        if (dto.getAutoDelete() != null) queue.setAutoDelete(dto.getAutoDelete());
        if (dto.getActive() != null) queue.setActive(dto.getActive());

        RabbitQueue updated = queueRepository.save(queue);
        if (Boolean.TRUE.equals(updated.getActive())) {
            declareQueueInRabbitMQ(updated);
        }

        log.info("[RABBIT-ADMIN] Queue actualizada: ID={} name={}", updated.getId(), updated.getName());
        return toQueueResponse(updated);
    }

    @Transactional
    public void deleteQueue(Long id) {
        RabbitQueue queue = queueRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Queue no encontrada con ID: " + id));

        if (Boolean.TRUE.equals(queue.getProtectedResource())) {
            log.warn("[RABBIT-ADMIN] Intento de eliminar queue protegida: {}", queue.getName());
            throw new ProtectedResourceException("La queue '" + queue.getName() + "' es un recurso protegido y no puede ser eliminada");
        }

        try {
            rabbitAdmin.deleteQueue(queue.getName());
        } catch (Exception ex) {
            log.warn("[RABBIT-ADMIN] Advertencia al eliminar queue en broker: {}", ex.getMessage());
        }

        queueRepository.delete(queue);
        log.info("[RABBIT-ADMIN] Queue eliminada de DB y RabbitMQ: name={}", queue.getName());
    }

    // =========================================================================
    // BINDINGS
    // =========================================================================

    @Transactional(readOnly = true)
    public List<RabbitBindingResponseDTO> listBindings() {
        return bindingRepository.findAll().stream()
                .map(this::toBindingResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RabbitBindingResponseDTO getBindingById(Long id) {
        RabbitBinding binding = bindingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Binding no encontrado con ID: " + id));
        return toBindingResponse(binding);
    }

    @Transactional
    public RabbitBindingResponseDTO createBinding(RabbitBindingDTO dto) {
        RabbitQueue queue = queueRepository.findById(dto.getQueueId())
                .orElseThrow(() -> new IllegalArgumentException("Queue no encontrada con ID: " + dto.getQueueId()));

        RabbitExchange exchange = exchangeRepository.findById(dto.getExchangeId())
                .orElseThrow(() -> new IllegalArgumentException("Exchange no encontrado con ID: " + dto.getExchangeId()));

        if (bindingRepository.existsByQueue_IdAndExchange_IdAndRoutingKey(dto.getQueueId(), dto.getExchangeId(), dto.getRoutingKey())) {
            throw new IllegalArgumentException(String.format("El binding entre queue '%s', exchange '%s' y routingKey '%s' ya existe",
                    queue.getName(), exchange.getName(), dto.getRoutingKey()));
        }

        RabbitBinding binding = new RabbitBinding();
        binding.setQueue(queue);
        binding.setExchange(exchange);
        binding.setRoutingKey(dto.getRoutingKey());
        binding.setProtectedResource(dto.getProtectedResource() != null ? dto.getProtectedResource() : false);
        binding.setActive(dto.getActive() != null ? dto.getActive() : true);

        RabbitBinding saved = bindingRepository.save(binding);

        try {
            if (Boolean.TRUE.equals(saved.getActive())) {
                declareBindingInRabbitMQ(saved);
            }
        } catch (Exception ex) {
            log.error("[RABBIT-ADMIN] Error declarando binding en RabbitMQ broker: {}", ex.getMessage());
            bindingRepository.delete(saved);
            throw new IllegalStateException("Error al declarar binding en RabbitMQ: " + ex.getMessage(), ex);
        }

        log.info("[RABBIT-ADMIN] Binding creado exitosamente: queue={} exchange={} routingKey={}",
                queue.getName(), exchange.getName(), dto.getRoutingKey());
        return toBindingResponse(saved);
    }

    @Transactional
    public RabbitBindingResponseDTO updateBinding(Long id, RabbitBindingDTO dto) {
        RabbitBinding binding = bindingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Binding no encontrado con ID: " + id));

        if (Boolean.TRUE.equals(binding.getProtectedResource()) && Boolean.FALSE.equals(dto.getActive())) {
            throw new ProtectedResourceException("No se puede desactivar el binding protegido con ID: " + id);
        }

        if (dto.getRoutingKey() != null) binding.setRoutingKey(dto.getRoutingKey());
        if (dto.getActive() != null) binding.setActive(dto.getActive());

        RabbitBinding updated = bindingRepository.save(binding);
        if (Boolean.TRUE.equals(updated.getActive())) {
            declareBindingInRabbitMQ(updated);
        }

        log.info("[RABBIT-ADMIN] Binding actualizado: ID={}", updated.getId());
        return toBindingResponse(updated);
    }

    @Transactional
    public void deleteBinding(Long id) {
        RabbitBinding binding = bindingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Binding no encontrado con ID: " + id));

        if (Boolean.TRUE.equals(binding.getProtectedResource())) {
            log.warn("[RABBIT-ADMIN] Intento de eliminar binding protegido: ID={}", id);
            throw new ProtectedResourceException("El binding con ID " + id + " es un recurso protegido y no puede ser eliminado");
        }

        try {
            Binding amqpBinding = new Binding(
                    binding.getQueue().getName(),
                    Binding.DestinationType.QUEUE,
                    binding.getExchange().getName(),
                    binding.getRoutingKey(),
                    null
            );
            rabbitAdmin.removeBinding(amqpBinding);
        } catch (Exception ex) {
            log.warn("[RABBIT-ADMIN] Advertencia al eliminar binding en broker: {}", ex.getMessage());
        }

        bindingRepository.delete(binding);
        log.info("[RABBIT-ADMIN] Binding eliminado de DB y RabbitMQ: ID={}", id);
    }

    // =========================================================================
    // RABBITMQ DECLARATION HELPERS
    // =========================================================================

    public void declareExchangeInRabbitMQ(RabbitExchange exchange) {
        Exchange ex;
        switch (exchange.getType().toUpperCase()) {
            case "DIRECT":
                ex = new DirectExchange(exchange.getName(), exchange.getDurable(), exchange.getAutoDelete());
                break;
            case "FANOUT":
                ex = new FanoutExchange(exchange.getName(), exchange.getDurable(), exchange.getAutoDelete());
                break;
            case "HEADERS":
                ex = new HeadersExchange(exchange.getName(), exchange.getDurable(), exchange.getAutoDelete());
                break;
            case "TOPIC":
            default:
                ex = new TopicExchange(exchange.getName(), exchange.getDurable(), exchange.getAutoDelete());
                break;
        }
        rabbitAdmin.declareExchange(ex);
        log.debug("[RABBIT-ADMIN] Declared exchange in broker: name={} type={}", exchange.getName(), exchange.getType());
    }

    public void declareQueueInRabbitMQ(RabbitQueue queue) {
        QueueBuilder builder = queue.getDurable() ? QueueBuilder.durable(queue.getName()) : QueueBuilder.nonDurable(queue.getName());
        if (Boolean.TRUE.equals(queue.getExclusive())) {
            builder.exclusive();
        }
        if (Boolean.TRUE.equals(queue.getAutoDelete())) {
            builder.autoDelete();
        }

        if (queue.getDeadLetterExchange() != null) {
            builder.withArgument("x-dead-letter-exchange", queue.getDeadLetterExchange().getName());
        }
        if (queue.getDeadLetterRoutingKey() != null && !queue.getDeadLetterRoutingKey().isBlank()) {
            builder.withArgument("x-dead-letter-routing-key", queue.getDeadLetterRoutingKey());
        }

        rabbitAdmin.declareQueue(builder.build());
        log.debug("[RABBIT-ADMIN] Declared queue in broker: name={}", queue.getName());
    }

    public void declareBindingInRabbitMQ(RabbitBinding binding) {
        Binding amqpBinding = new Binding(
                binding.getQueue().getName(),
                Binding.DestinationType.QUEUE,
                binding.getExchange().getName(),
                binding.getRoutingKey(),
                null
        );
        rabbitAdmin.declareBinding(amqpBinding);
        log.debug("[RABBIT-ADMIN] Declared binding in broker: queue={} exchange={} routingKey={}",
                binding.getQueue().getName(), binding.getExchange().getName(), binding.getRoutingKey());
    }

    // =========================================================================
    // MAPPERS
    // =========================================================================

    private RabbitExchangeResponseDTO toExchangeResponse(RabbitExchange entity) {
        RabbitExchangeResponseDTO dto = new RabbitExchangeResponseDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setType(entity.getType());
        dto.setDurable(entity.getDurable());
        dto.setAutoDelete(entity.getAutoDelete());
        dto.setInternal(entity.getInternal());
        dto.setProtectedResource(entity.getProtectedResource());
        dto.setActive(entity.getActive());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private RabbitQueueResponseDTO toQueueResponse(RabbitQueue entity) {
        RabbitQueueResponseDTO dto = new RabbitQueueResponseDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDurable(entity.getDurable());
        dto.setExclusive(entity.getExclusive());
        dto.setAutoDelete(entity.getAutoDelete());
        if (entity.getDeadLetterExchange() != null) {
            dto.setDeadLetterExchangeId(entity.getDeadLetterExchange().getId());
            dto.setDeadLetterExchangeName(entity.getDeadLetterExchange().getName());
        }
        dto.setDeadLetterRoutingKey(entity.getDeadLetterRoutingKey());
        dto.setProtectedResource(entity.getProtectedResource());
        dto.setActive(entity.getActive());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }

    private RabbitBindingResponseDTO toBindingResponse(RabbitBinding entity) {
        RabbitBindingResponseDTO dto = new RabbitBindingResponseDTO();
        dto.setId(entity.getId());
        if (entity.getQueue() != null) {
            dto.setQueueId(entity.getQueue().getId());
            dto.setQueueName(entity.getQueue().getName());
        }
        if (entity.getExchange() != null) {
            dto.setExchangeId(entity.getExchange().getId());
            dto.setExchangeName(entity.getExchange().getName());
        }
        dto.setRoutingKey(entity.getRoutingKey());
        dto.setProtectedResource(entity.getProtectedResource());
        dto.setActive(entity.getActive());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
