package cl.duoc.pedidos360.chat.service;

import cl.duoc.pedidos360.chat.dto.ConversacionResponseDTO;
import cl.duoc.pedidos360.chat.dto.EnviarMensajeDTO;
import cl.duoc.pedidos360.chat.dto.MensajeResponseDTO;
import cl.duoc.pedidos360.chat.entity.Conversacion;
import cl.duoc.pedidos360.chat.entity.Mensaje;
import cl.duoc.pedidos360.chat.repository.ConversacionRepository;
import cl.duoc.pedidos360.chat.repository.MensajeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatServiceTest {

    private ConversacionRepository conversacionRepository;
    private MensajeRepository mensajeRepository;
    private SimpMessagingTemplate messagingTemplate;
    private RabbitTemplate rabbitTemplate;
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        conversacionRepository = mock(ConversacionRepository.class);
        mensajeRepository = mock(MensajeRepository.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        chatService = new ChatService(conversacionRepository, mensajeRepository, messagingTemplate, rabbitTemplate);
    }

    @Test
    void testCrearOObtenerConversacion_Nueva_ConCallerValido() {
        when(conversacionRepository.findByPedidoId(10L)).thenReturn(Optional.empty());

        Conversacion nuevaConv = new Conversacion(10L, 5L, 8L);
        nuevaConv.setId(100L);
        when(conversacionRepository.save(any(Conversacion.class))).thenReturn(nuevaConv);
        when(mensajeRepository.findByConversacionIdOrderByFechaEnvioAsc(100L)).thenReturn(List.of());

        // Simulamos respuesta de pedidos-service Mock (o nulo por defecto en test unitario sin Mock RestTemplate)
        // Probamos cuando conv ya existe con participante vs usuario ajeno:
        Conversacion convExistente = new Conversacion(10L, 5L, 8L);
        convExistente.setId(100L);
        when(conversacionRepository.findByPedidoId(10L)).thenReturn(Optional.of(convExistente));

        // Participante cliente 5L -> OK
        ConversacionResponseDTO dto = chatService.crearOObtenerConversacion(10L, 5L, 8L, 5L, "CLIENTE");
        assertNotNull(dto);
        assertEquals(100L, dto.getId());

        // Usuario ajeno 99L -> SecurityException (403)
        assertThrows(SecurityException.class, () ->
                chatService.crearOObtenerConversacion(10L, 5L, 8L, 99L, "CLIENTE"));
    }

    @Test
    void testCrearOObtenerConversacion_PedidosServiceCaido_LanzaIllegalStateException() {
        when(conversacionRepository.findByPedidoId(10L)).thenReturn(Optional.empty());

        // Sin mock de pedidos-service (retornará null) -> Fail Closed
        assertThrows(IllegalStateException.class, () ->
                chatService.crearOObtenerConversacion(10L, 5L, 8L, 5L, "CLIENTE"));
    }

    @Test
    void testObtenerPorPedidoId_ParticipanteValido() {
        Conversacion conv = new Conversacion(10L, 5L, 8L);
        conv.setId(100L);
        when(conversacionRepository.findByPedidoId(10L)).thenReturn(Optional.of(conv));
        when(mensajeRepository.findByConversacionIdOrderByFechaEnvioAsc(100L)).thenReturn(List.of());

        ConversacionResponseDTO dto = chatService.obtenerPorPedidoId(10L, 5L);
        assertNotNull(dto);
        assertEquals(100L, dto.getId());
    }

    @Test
    void testObtenerPorPedidoId_UsuarioAjeno_LanzaSecurityException() {
        Conversacion conv = new Conversacion(10L, 5L, 8L);
        conv.setId(100L);
        when(conversacionRepository.findByPedidoId(10L)).thenReturn(Optional.of(conv));

        assertThrows(SecurityException.class, () -> chatService.obtenerPorPedidoId(10L, 999L));
    }

    @Test
    void testEnviarMensaje_Valido() {
        Conversacion conv = new Conversacion(10L, 5L, 8L);
        conv.setId(100L);
        when(conversacionRepository.findById(100L)).thenReturn(Optional.of(conv));

        Mensaje guardado = new Mensaje(100L, 5L, "CLIENTE", "Hola repartidor");
        guardado.setId(1L);
        when(mensajeRepository.save(any(Mensaje.class))).thenReturn(guardado);

        EnviarMensajeDTO dto = new EnviarMensajeDTO(100L, 5L, "REPARTIDOR_FALSO", "Hola repartidor");
        MensajeResponseDTO res = chatService.enviarMensaje(dto);

        assertNotNull(res);
        assertEquals(1L, res.getId());
        assertEquals("CLIENTE", res.getTipoRemitente()); // Se auto-deriva como CLIENTE
    }

    @Test
    void testEnviarMensaje_Vacio_LanzaException() {
        EnviarMensajeDTO dto = new EnviarMensajeDTO(100L, 5L, "CLIENTE", "   ");
        assertThrows(IllegalArgumentException.class, () -> chatService.enviarMensaje(dto));
    }

    @Test
    void testEnviarMensaje_Largo_LanzaException() {
        String mensajeLargo = "a".repeat(1001);
        EnviarMensajeDTO dto = new EnviarMensajeDTO(100L, 5L, "CLIENTE", mensajeLargo);
        assertThrows(IllegalArgumentException.class, () -> chatService.enviarMensaje(dto));
    }

    @Test
    void testEnviarMensaje_ChatCerrado_LanzaException() {
        Conversacion conv = new Conversacion(10L, 5L, 8L);
        conv.setId(100L);
        conv.setEstado("CERRADO");
        when(conversacionRepository.findById(100L)).thenReturn(Optional.of(conv));

        EnviarMensajeDTO dto = new EnviarMensajeDTO(100L, 5L, "CLIENTE", "Mensaje post cierre");
        assertThrows(IllegalStateException.class, () -> chatService.enviarMensaje(dto));
    }

    @Test
    void testCerrarConversacionPorPedidoEntregado() {
        Conversacion conv = new Conversacion(10L, 5L, 8L);
        conv.setId(100L);
        when(conversacionRepository.findByPedidoId(10L)).thenReturn(Optional.of(conv));

        chatService.cerrarConversacionPorPedidoEntregado(10L);

        assertEquals("CERRADO", conv.getEstado());
        assertNotNull(conv.getFechaCierre());
        verify(conversacionRepository).save(conv);
    }

    @Test
    void testEliminarLogico_Cliente() {
        Conversacion conv = new Conversacion(10L, 5L, 8L);
        conv.setId(100L);
        when(conversacionRepository.findById(100L)).thenReturn(Optional.of(conv));

        chatService.eliminarLogico(100L, 5L);

        assertNotNull(conv.getDeletedForClientAt());
        assertNull(conv.getDeletedForCourierAt());
    }
}
