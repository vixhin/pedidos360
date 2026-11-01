package cl.duoc.pedidos360.usuario.controller;

import cl.duoc.pedidos360.usuario.dto.ApiResponse;
import cl.duoc.pedidos360.usuario.dto.PasswordChangeDTO;
import cl.duoc.pedidos360.usuario.dto.UsuarioCreateDTO;
import cl.duoc.pedidos360.usuario.dto.UsuarioResponseDTO;
import cl.duoc.pedidos360.usuario.service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/usuario")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    private String getAuthenticatedEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equalsIgnoreCase(auth.getName())) {
            return null;
        }
        return auth.getName();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UsuarioResponseDTO>> crearUsuario(@Valid @RequestBody UsuarioCreateDTO dto) {
        UsuarioResponseDTO creado = usuarioService.crearUsuario(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Usuario creado correctamente", creado));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<UsuarioResponseDTO>>> listarUsuarios() {
        List<UsuarioResponseDTO> usuarios = usuarioService.obtenerTodos();
        return ResponseEntity.ok(ApiResponse.ok("Lista de usuarios obtenida correctamente", usuarios));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UsuarioResponseDTO>> obtenerPerfilPropio() {
        String email = getAuthenticatedEmail();
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Identidad de usuario no autenticada"));
        }
        try {
            UsuarioResponseDTO usuario = usuarioService.obtenerPorEmail(email);
            return ResponseEntity.ok(ApiResponse.ok("Perfil obtenido exitosamente", usuario));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Usuario no encontrado"));
        }
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UsuarioResponseDTO>> actualizarPerfilPropio(@RequestBody UsuarioCreateDTO dto) {
        String email = getAuthenticatedEmail();
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Identidad de usuario no autenticada"));
        }
        try {
            UsuarioResponseDTO actual = usuarioService.obtenerPorEmail(email);
            // Prevenir escalamiento de privilegios: perfil propio no puede cambiar rol ni estado
            dto.setRol(actual.getRol());
            dto.setEstado(actual.getEstado());
            UsuarioResponseDTO actualizado = usuarioService.actualizarUsuario(actual.getId(), dto);
            return ResponseEntity.ok(ApiResponse.ok("Perfil actualizado correctamente", actualizado));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PutMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> cambiarPasswordPropia(@Valid @RequestBody PasswordChangeDTO dto) {
        String email = getAuthenticatedEmail();
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Identidad de usuario no autenticada"));
        }
        try {
            usuarioService.cambiarPasswordPorEmail(email, dto.getCurrentPassword(), dto.getNewPassword());
            return ResponseEntity.ok(ApiResponse.ok("Contraseña actualizada exitosamente", null));
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(iae.getMessage()));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UsuarioResponseDTO>> obtenerPorId(@PathVariable Long id) {
        UsuarioResponseDTO usuario = usuarioService.obtenerPorId(id);
        return ResponseEntity.ok(ApiResponse.ok("Usuario encontrado", usuario));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UsuarioResponseDTO>> actualizarUsuario(@PathVariable Long id,
                                                                              @Valid @RequestBody UsuarioCreateDTO dto) {
        UsuarioResponseDTO actualizado = usuarioService.actualizarUsuario(id, dto);
        return ResponseEntity.ok(ApiResponse.ok("Usuario actualizado correctamente", actualizado));
    }

    @PutMapping("/{id}/password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> cambiarPassword(@PathVariable Long id,
                                                              @Valid @RequestBody PasswordChangeDTO dto) {
        try {
            usuarioService.cambiarPassword(id, dto.getCurrentPassword(), dto.getNewPassword());
            return ResponseEntity.ok(ApiResponse.ok("Contraseña actualizada exitosamente", null));
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(iae.getMessage()));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UsuarioResponseDTO>> actualizarEstadoPut(
            @PathVariable Long id,
            @RequestParam String status) {
        UsuarioResponseDTO actualizado = usuarioService.actualizarEstado(id, status);
        return ResponseEntity.ok(ApiResponse.ok("Estado actualizado correctamente", actualizado));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UsuarioResponseDTO>> actualizarEstadoPatch(
            @PathVariable Long id,
            @RequestParam String status) {
        UsuarioResponseDTO actualizado = usuarioService.actualizarEstado(id, status);
        return ResponseEntity.ok(ApiResponse.ok("Estado actualizado correctamente", actualizado));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> eliminarUsuario(@PathVariable Long id) {
        usuarioService.eliminarUsuario(id);
        return ResponseEntity.ok(ApiResponse.ok("Usuario eliminado correctamente", null));
    }
}
