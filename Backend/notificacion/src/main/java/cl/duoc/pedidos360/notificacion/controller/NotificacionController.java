package cl.duoc.pedidos360.notificacion.controller;

import cl.duoc.pedidos360.notificacion.dto.NotificacionRequestDTO;
import cl.duoc.pedidos360.notificacion.entity.Notificacion;
import cl.duoc.pedidos360.notificacion.service.NotificacionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/notificacion")
public class NotificacionController {

    private final NotificacionService notificacionService;

    public NotificacionController(NotificacionService notificacionService) {
        this.notificacionService = notificacionService;
    }

    @GetMapping
    public ResponseEntity<List<Notificacion>> listar(@Autowired(required = false) HttpServletRequest request) {
        if (request != null) {
            String role = (String) request.getAttribute("p360.role");
            Long callerId = (Long) request.getAttribute("p360.userId");
            if (role == null || !role.contains("ADMIN")) {
                if (callerId != null) {
                    return ResponseEntity.ok(notificacionService.obtenerPorUsuario(callerId));
                }
            }
        }
        return ResponseEntity.ok(notificacionService.obtenerTodas());
    }

    public ResponseEntity<List<Notificacion>> listar() {
        return listar(null);
    }

    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<Notificacion>> obtenerPorUsuario(@PathVariable Long usuarioId, @Autowired(required = false) HttpServletRequest request) {
        if (request != null) {
            validarPropietarioOAdmin(request, usuarioId);
        }
        return ResponseEntity.ok(notificacionService.obtenerPorUsuario(usuarioId));
    }

    public ResponseEntity<List<Notificacion>> obtenerPorUsuario(Long usuarioId) {
        return obtenerPorUsuario(usuarioId, null);
    }

    @PostMapping
    public ResponseEntity<Notificacion> enviar(@RequestBody NotificacionRequestDTO notificacion,
                                               @Autowired(required = false) HttpServletRequest request) {
        Notificacion nueva = notificacion.toEntity();
        if (request != null && !esAdminOVendedor(request)) {
            // Un cliente sólo puede crearse notificaciones a sí mismo (evita suplantar avisos a otros usuarios)
            Long callerId = (Long) request.getAttribute("p360.userId");
            if (callerId == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Se requiere autenticación.");
            }
            nueva.setUsuarioId(callerId);
        }
        return ResponseEntity.ok(notificacionService.enviarNotificacion(nueva));
    }

    public ResponseEntity<Notificacion> enviar(NotificacionRequestDTO notificacion) {
        return enviar(notificacion, null);
    }

    @PutMapping("/{id}/leida")
    public ResponseEntity<Void> marcarLeida(@PathVariable Long id, @Autowired(required = false) HttpServletRequest request) {
        if (request != null) {
            // Evita IDOR: sólo el destinatario (o ADMIN/VENDEDOR) puede marcarla como leída
            Notificacion existente = notificacionService.obtenerPorId(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notificación no encontrada."));
            validarPropietarioOAdmin(request, existente.getUsuarioId());
        }
        notificacionService.marcarLeida(id);
        return ResponseEntity.ok().build();
    }

    public ResponseEntity<Void> marcarLeida(Long id) {
        return marcarLeida(id, null);
    }

    private boolean esAdminOVendedor(HttpServletRequest request) {
        String role = (String) request.getAttribute("p360.role");
        return role != null && (role.contains("ADMIN") || role.contains("VENDEDOR"));
    }

    @PutMapping("/usuario/{usuarioId}/marcar-todas-leidas")
    public ResponseEntity<Void> marcarTodasLeidas(@PathVariable Long usuarioId, @Autowired(required = false) HttpServletRequest request) {
        if (request != null) {
            validarPropietarioOAdmin(request, usuarioId);
        }
        notificacionService.marcarTodasLeidas(usuarioId);
        return ResponseEntity.ok().build();
    }

    public ResponseEntity<Void> marcarTodasLeidas(Long usuarioId) {
        return marcarTodasLeidas(usuarioId, null);
    }

    private void validarPropietarioOAdmin(HttpServletRequest request, Long targetUsuarioId) {
        Long callerId = (Long) request.getAttribute("p360.userId");
        String role = (String) request.getAttribute("p360.role");

        if (role != null && (role.contains("ADMIN") || role.contains("VENDEDOR"))) {
            return;
        }

        if (callerId == null || !callerId.equals(targetUsuarioId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso denegado: No tiene permisos para consultar o modificar las notificaciones de otro usuario.");
        }
    }
}
