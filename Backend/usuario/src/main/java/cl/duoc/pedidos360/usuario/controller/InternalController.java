package cl.duoc.pedidos360.usuario.controller;

import cl.duoc.pedidos360.usuario.dto.ApiResponse;
import cl.duoc.pedidos360.usuario.dto.EntraSyncRequest;
import cl.duoc.pedidos360.usuario.dto.UsuarioResponseDTO;
import cl.duoc.pedidos360.usuario.service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * InternalController — Endpoint interno de comunicación entre microservicios (BFF -> usuario-service).
 * No expuesto directamente a clientes externos/navegador.
 * Protegido mediante cabecera X-Internal-Service-Key.
 */
@RestController
@RequestMapping("/api/internal")
public class InternalController {

    private static final Logger log = LoggerFactory.getLogger(InternalController.class);

    private final UsuarioService usuarioService;
    private final String internalKey;

    public InternalController(
            UsuarioService usuarioService,
            @Value("${bff.internal-key:pedidos360-internal-secret-key-2026}") String internalKey) {
        this.usuarioService = usuarioService;
        this.internalKey = internalKey;
    }

    @PostMapping("/entra-sync")
    public ResponseEntity<?> syncInternal(HttpServletRequest request, @RequestBody EntraSyncRequest dto) {
        String clientKey = request.getHeader("X-Internal-Service-Key");

        if (clientKey == null || !clientKey.equals(internalKey)) {
            log.warn("[USER-SERVICE][INTERNAL] Unauthorized internal sync attempt. Invalid/missing X-Internal-Service-Key");
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Acceso denegado: Clave de servicio interno inválida o faltante"));
        }

        log.info("[USER-SERVICE][INTERNAL] Internal sync request verified for email: {} with role: {}", dto.getEmail(), dto.getRol());

        UsuarioResponseDTO response = usuarioService.sincronizarUsuarioExterno(
                dto.getEmail(), dto.getNombre(), dto.getRol());

        return ResponseEntity.ok(ApiResponse.ok("Usuario sincronizado internamente", response));
    }
}
