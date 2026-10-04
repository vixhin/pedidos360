package cl.duoc.pedidos360.chat.controller;

import cl.duoc.pedidos360.chat.dto.*;
import cl.duoc.pedidos360.chat.service.ChatService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/conversacion")
    public ResponseEntity<ConversacionResponseDTO> crearOObtenerConversacion(@RequestParam Long pedidoId,
                                                                             @RequestParam Long clienteId,
                                                                             @RequestParam Long repartidorId) {
        ConversacionResponseDTO response = chatService.crearOObtenerConversacion(pedidoId, clienteId, repartidorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/pedido/{pedidoId}")
    public ResponseEntity<?> obtenerPorPedidoId(@PathVariable Long pedidoId,
                                                @RequestParam Long usuarioId) {
        try {
            ConversacionResponseDTO response = chatService.obtenerPorPedidoId(pedidoId, usuarioId);
            return ResponseEntity.ok(response);
        } catch (SecurityException se) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", se.getMessage()));
        } catch (IllegalArgumentException ie) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", ie.getMessage()));
        }
    }

    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<ConversacionResponseDTO>> obtenerConversacionesPorUsuario(@PathVariable Long usuarioId) {
        return ResponseEntity.ok(chatService.obtenerConversacionesPorUsuario(usuarioId));
    }

    @PostMapping("/mensaje")
    public ResponseEntity<?> enviarMensaje(@RequestBody EnviarMensajeDTO dto) {
        try {
            MensajeResponseDTO msg = chatService.enviarMensaje(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(msg);
        } catch (SecurityException se) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", se.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException ie) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "message", ie.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    @DeleteMapping("/conversacion/{id}")
    public ResponseEntity<?> eliminarLogico(@PathVariable Long id, @RequestParam Long usuarioId) {
        try {
            chatService.eliminarLogico(id, usuarioId);
            return ResponseEntity.ok(Map.of("success", true, "message", "Conversación ocultada exitosamente"));
        } catch (SecurityException se) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", se.getMessage()));
        }
    }
}
