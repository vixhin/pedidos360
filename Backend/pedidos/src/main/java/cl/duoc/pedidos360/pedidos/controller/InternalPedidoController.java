package cl.duoc.pedidos360.pedidos.controller;

import cl.duoc.pedidos360.pedidos.dto.InternalPedidoResponseDTO;
import cl.duoc.pedidos360.pedidos.service.PedidoService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * InternalPedidoController — Endpoint interno de comunicación entre microservicios (chat -> pedidos-service).
 * No expuesto directamente a clientes externos/navegador.
 * Protegido mediante la cabecera X-Internal-Service-Key.
 */
@RestController
@RequestMapping("/api/internal/pedidos")
public class InternalPedidoController {

    private static final Logger log = LoggerFactory.getLogger(InternalPedidoController.class);

    private final PedidoService pedidoService;
    private final String internalKey;

    public InternalPedidoController(
            PedidoService pedidoService,
            @Value("${bff.internal-key:${BFF_INTERNAL_KEY:pedidos360-internal-secret-key-local-2026}}") String internalKey) {
        this.pedidoService = pedidoService;
        this.internalKey = internalKey;
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerPorIdInternal(HttpServletRequest request, @PathVariable Long id) {
        String clientKey = request.getHeader("X-Internal-Service-Key");

        if (clientKey == null || !clientKey.equals(internalKey)) {
            log.warn("[PEDIDOS-SERVICE][INTERNAL] Unauthorized internal order lookup attempt. Invalid/missing X-Internal-Service-Key");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "message", "Acceso denegado: Clave de servicio interno inválida o faltante"));
        }

        return pedidoService.obtenerPorId(id)
                .map(p -> {
                    InternalPedidoResponseDTO dto = new InternalPedidoResponseDTO(
                            p.getId(),
                            p.getUsuarioId(),
                            p.getRepartidorId(),
                            p.getEstado(),
                            p.getTotal()
                    );
                    return ResponseEntity.ok(dto);
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }
}
