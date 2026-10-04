package cl.duoc.pedidos360.chat.controller;

import cl.duoc.pedidos360.chat.dto.ConversacionResponseDTO;
import cl.duoc.pedidos360.chat.dto.EnviarMensajeDTO;
import cl.duoc.pedidos360.chat.dto.MensajeResponseDTO;
import cl.duoc.pedidos360.chat.security.JwtUtil;
import cl.duoc.pedidos360.chat.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ChatService chatService;

    @Mock
    private JwtUtil jwtUtil;

    private ChatController chatController;
    private final String internalKey = "pedidos360-internal-secret-key-local-2026";

    @BeforeEach
    void setUp() {
        chatController = new ChatController(chatService, jwtUtil);
        org.springframework.test.util.ReflectionTestUtils.setField(chatController, "bffInternalKey", internalKey);
    }

    @Test
    @DisplayName("Sin autenticación ni S2S key -> 403 Forbidden")
    void testRequestSinAuthReturns403() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseEntity<?> response = chatController.crearOObtenerConversacion(10L, 5L, 8L, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("Request con S2S key e ID de usuario de BFF -> procesado con ID real")
    void testRequestConS2SKeyValida() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Internal-Service-Key", internalKey);
        request.addHeader("X-User-Id", "5");
        request.addHeader("X-User-Roles", "CLIENTE");

        ConversacionResponseDTO mockConv = new ConversacionResponseDTO(100L, 10L, 5L, 8L, "ABIERTO", null, null, List.of());
        when(chatService.crearOObtenerConversacion(eq(10L), eq(5L), eq(8L), eq(5L), eq("CLIENTE"))).thenReturn(mockConv);

        ResponseEntity<?> response = chatController.crearOObtenerConversacion(10L, 5L, 8L, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    @DisplayName("Enviar mensaje con remitenteId falso en body -> usa ID autenticado de la cabecera S2S")
    void testEnviarMensajeIgnoresFakeRemitenteId() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Internal-Service-Key", internalKey);
        request.addHeader("X-User-Id", "5");
        request.addHeader("X-User-Roles", "CLIENTE");

        EnviarMensajeDTO dto = new EnviarMensajeDTO(100L, 9999L, "REPARTIDOR", "Hola");
        MensajeResponseDTO msgDTO = new MensajeResponseDTO(1L, 100L, 5L, "CLIENTE", "Hola", "ENVIADO", null, null, null);

        when(chatService.enviarMensaje(any(EnviarMensajeDTO.class), eq(5L), eq("CLIENTE"))).thenReturn(msgDTO);

        ResponseEntity<?> response = chatController.enviarMensaje(dto, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(chatService).enviarMensaje(dto, 5L, "CLIENTE");
    }
}
