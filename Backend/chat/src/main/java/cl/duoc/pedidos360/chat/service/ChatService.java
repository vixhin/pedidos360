package cl.duoc.pedidos360.chat.service;

import cl.duoc.pedidos360.chat.config.RabbitMQConfig;
import cl.duoc.pedidos360.chat.dto.*;
import cl.duoc.pedidos360.chat.entity.Conversacion;
import cl.duoc.pedidos360.chat.entity.Mensaje;
import cl.duoc.pedidos360.chat.repository.ConversacionRepository;
import cl.duoc.pedidos360.chat.repository.MensajeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ConversacionRepository conversacionRepository;
    private final MensajeRepository mensajeRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final RabbitTemplate rabbitTemplate;

    public ChatService(ConversacionRepository conversacionRepository,
                       MensajeRepository mensajeRepository,
                       SimpMessagingTemplate messagingTemplate,
                       RabbitTemplate rabbitTemplate) {
        this.conversacionRepository = conversacionRepository;
        this.mensajeRepository = mensajeRepository;
        this.messagingTemplate = messagingTemplate;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Transactional
    public ConversacionResponseDTO crearOObtenerConversacion(Long pedidoId, Long clienteId, Long repartidorId) {
        Optional<Conversacion> existente = conversacionRepository.findByPedidoId(pedidoId);
        Conversacion conv;
        if (existente.isPresent()) {
            conv = existente.get();
        } else {
            conv = new Conversacion(pedidoId, clienteId, repartidorId);
            conv = conversacionRepository.save(conv);
            log.info("[CHAT-SERVICE] Chat conversation created for order ID={}, clienteId={}, repartidorId={}",
                    pedidoId, clienteId, repartidorId);

            // Publicar evento chat.creado en RabbitMQ
            try {
                Map<String, Object> event = new HashMap<>();
                event.put("eventId", UUID.randomUUID().toString());
                event.put("conversacionId", conv.getId());
                event.put("pedidoId", pedidoId);
                event.put("clienteId", clienteId);
                event.put("repartidorId", repartidorId);
                event.put("timestamp", LocalDateTime.now().toString());

                rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_EVENTS, RabbitMQConfig.ROUTING_KEY_CHAT_CREADO, event);
            } catch (Exception e) {
                log.warn("[CHAT-SERVICE] Failed to publish chat.creado event: {}", e.getMessage());
            }
        }

        return mapConversacionDTO(conv);
    }

    public ConversacionResponseDTO obtenerPorPedidoId(Long pedidoId, Long usuarioId) {
        Conversacion conv = conversacionRepository.findByPedidoId(pedidoId)
                .orElseThrow(() -> new IllegalArgumentException("No existe chat para el pedido ID: " + pedidoId));

        validarParticipante(conv, usuarioId);
        return mapConversacionDTO(conv);
    }

    public List<ConversacionResponseDTO> obtenerConversacionesPorUsuario(Long usuarioId) {
        List<Conversacion> clienteList = conversacionRepository.findByClienteIdAndDeletedForClientAtIsNull(usuarioId);
        List<Conversacion> repartidorList = conversacionRepository.findByRepartidorIdAndDeletedForCourierAtIsNull(usuarioId);

        Set<Conversacion> todas = new HashSet<>();
        todas.addAll(clienteList);
        todas.addAll(repartidorList);

        return todas.stream()
                .map(this::mapConversacionDTO)
                .sorted(Comparator.comparing(ConversacionResponseDTO::getFechaCreacion).reversed())
                .collect(Collectors.toList());
    }

    @Transactional
    public MensajeResponseDTO enviarMensaje(EnviarMensajeDTO dto) {
        Conversacion conv = conversacionRepository.findById(dto.getConversacionId())
                .orElseThrow(() -> new IllegalArgumentException("Conversación no encontrada con ID: " + dto.getConversacionId()));

        if ("CERRADO".equalsIgnoreCase(conv.getEstado()) || "ELIMINADO".equalsIgnoreCase(conv.getEstado())) {
            throw new IllegalStateException("Esta conversación fue cerrada porque el pedido fue entregado.");
        }

        validarParticipante(conv, dto.getRemitenteId());

        Mensaje msg = new Mensaje(conv.getId(), dto.getRemitenteId(), dto.getTipoRemitente(), dto.getContenido());
        msg = mensajeRepository.save(msg);

        MensajeResponseDTO responseDTO = mapMensajeDTO(msg);

        // Transmitir vía WebSocket en tiempo real al tópico del chat
        try {
            messagingTemplate.convertAndSend("/topic/chat/" + conv.getId(), responseDTO);
        } catch (Exception e) {
            log.warn("[CHAT-SERVICE] Failed to broadcast WS message: {}", e.getMessage());
        }

        // Publicar evento chat.mensaje.enviado a RabbitMQ
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventId", UUID.randomUUID().toString());
            event.put("mensajeId", msg.getId());
            event.put("conversacionId", conv.getId());
            event.put("pedidoId", conv.getPedidoId());
            event.put("remitenteId", dto.getRemitenteId());
            event.put("tipoRemitente", dto.getTipoRemitente());
            event.put("contenido", dto.getContenido());
            event.put("timestamp", LocalDateTime.now().toString());

            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_EVENTS, RabbitMQConfig.ROUTING_KEY_CHAT_MENSAJE_ENVIADO, event);
        } catch (Exception e) {
            log.warn("[CHAT-SERVICE] Failed to publish chat.mensaje.enviado event: {}", e.getMessage());
        }

        return responseDTO;
    }

    @Transactional
    public void cerrarConversacionPorPedidoEntregado(Long pedidoId) {
        Optional<Conversacion> opt = conversacionRepository.findByPedidoId(pedidoId);
        if (opt.isPresent()) {
            Conversacion conv = opt.get();
            conv.setEstado("CERRADO");
            conv.setFechaCierre(LocalDateTime.now());
            conv.setUpdatedAt(LocalDateTime.now());
            conversacionRepository.save(conv);
            log.info("[CHAT-SERVICE] Conversation ID={} closed automatically due to order delivery.", conv.getId());

            // Avisar por WebSocket
            try {
                Map<String, Object> statusMsg = Map.of(
                        "tipo", "CHAT_CERRADO",
                        "mensaje", "Esta conversación fue cerrada porque el pedido fue entregado.",
                        "conversacionId", conv.getId()
                );
                messagingTemplate.convertAndSend("/topic/chat/" + conv.getId() + "/status", statusMsg);
            } catch (Exception e) {
                log.warn("[CHAT-SERVICE] Failed to broadcast closure WS event: {}", e.getMessage());
            }
        }
    }

    @Transactional
    public void eliminarLogico(Long conversacionId, Long usuarioId) {
        Conversacion conv = conversacionRepository.findById(conversacionId)
                .orElseThrow(() -> new IllegalArgumentException("Conversación no encontrada con ID: " + conversacionId));

        validarParticipante(conv, usuarioId);

        if (usuarioId.equals(conv.getClienteId())) {
            conv.setDeletedForClientAt(LocalDateTime.now());
        }
        if (usuarioId.equals(conv.getRepartidorId())) {
            conv.setDeletedForCourierAt(LocalDateTime.now());
        }

        conv.setUpdatedAt(LocalDateTime.now());
        conversacionRepository.save(conv);
        log.info("[CHAT-SERVICE] Logical deletion applied for conversation ID={} by user ID={}", conversacionId, usuarioId);
    }

    private void validarParticipante(Conversacion conv, Long usuarioId) {
        if (!usuarioId.equals(conv.getClienteId()) && !usuarioId.equals(conv.getRepartidorId())) {
            throw new SecurityException("Acceso denegado: El usuario no es participante del pedido.");
        }
    }

    private ConversacionResponseDTO mapConversacionDTO(Conversacion c) {
        List<Mensaje> msgs = mensajeRepository.findByConversacionIdOrderByFechaEnvioAsc(c.getId());
        List<MensajeResponseDTO> msgDTOs = msgs.stream().map(this::mapMensajeDTO).collect(Collectors.toList());

        return new ConversacionResponseDTO(
                c.getId(),
                c.getPedidoId(),
                c.getClienteId(),
                c.getRepartidorId(),
                c.getEstado(),
                c.getFechaCreacion(),
                c.getFechaCierre(),
                msgDTOs
        );
    }

    private MensajeResponseDTO mapMensajeDTO(Mensaje m) {
        return new MensajeResponseDTO(
                m.getId(),
                m.getConversacionId(),
                m.getRemitenteId(),
                m.getTipoRemitente(),
                m.getContenido(),
                m.getEstado(),
                m.getFechaEnvio(),
                m.getFechaEntrega(),
                m.getFechaLectura()
        );
    }
}
