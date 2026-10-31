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
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/usuario")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UsuarioResponseDTO>> crearUsuario(@Valid @RequestBody UsuarioCreateDTO dto) {
        UsuarioResponseDTO creado = usuarioService.crearUsuario(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Usuario creado correctamente", creado));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<UsuarioResponseDTO>>> listarUsuarios() {
        List<UsuarioResponseDTO> usuarios = usuarioService.obtenerTodos();
        return ResponseEntity.ok(ApiResponse.ok("Lista de usuarios obtenida correctamente", usuarios));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UsuarioResponseDTO>> obtenerPerfilPropio(HttpServletRequest request) {
        String email = request.getHeader("X-User-Email");
        if (email == null || email.isBlank()) {
            email = request.getHeader("X-User-Sub");
        }
        if (email == null || email.isBlank() || "anonymous".equalsIgnoreCase(email)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Identidad de usuario no proporcionada en la cabecera"));
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
    public ResponseEntity<ApiResponse<UsuarioResponseDTO>> actualizarPerfilPropio(
            HttpServletRequest request,
            @RequestBody UsuarioCreateDTO dto) {
        String email = request.getHeader("X-User-Email");
        if (email == null || email.isBlank()) {
            email = request.getHeader("X-User-Sub");
        }
        if (email == null || email.isBlank() || "anonymous".equalsIgnoreCase(email)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Identidad de usuario no proporcionada en la cabecera"));
        }
        try {
            UsuarioResponseDTO actual = usuarioService.obtenerPorEmail(email);
            UsuarioResponseDTO actualizado = usuarioService.actualizarUsuario(actual.getId(), dto);
            return ResponseEntity.ok(ApiResponse.ok("Perfil actualizado correctamente", actualizado));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(ex.getMessage()));
        }
    }

    @PutMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> cambiarPasswordPropia(
            HttpServletRequest request,
            @Valid @RequestBody PasswordChangeDTO dto) {
        String email = request.getHeader("X-User-Email");
        if (email == null || email.isBlank()) {
            email = request.getHeader("X-User-Sub");
        }
        if (email == null || email.isBlank() || "anonymous".equalsIgnoreCase(email)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Identidad de usuario no proporcionada"));
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
    public ResponseEntity<ApiResponse<UsuarioResponseDTO>> obtenerPorId(@PathVariable Long id) {
        UsuarioResponseDTO usuario = usuarioService.obtenerPorId(id);
        return ResponseEntity.ok(ApiResponse.ok("Usuario encontrado", usuario));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UsuarioResponseDTO>> actualizarUsuario(@PathVariable Long id,
                                                                              @Valid @RequestBody UsuarioCreateDTO dto) {
        UsuarioResponseDTO actualizado = usuarioService.actualizarUsuario(id, dto);
        return ResponseEntity.ok(ApiResponse.ok("Usuario actualizado correctamente", actualizado));
    }

    @PutMapping("/{id}/password")
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
    public ResponseEntity<ApiResponse<UsuarioResponseDTO>> actualizarEstadoPut(
            @PathVariable Long id,
            @RequestParam String status) {
        UsuarioResponseDTO actualizado = usuarioService.actualizarEstado(id, status);
        return ResponseEntity.ok(ApiResponse.ok("Estado actualizado correctamente", actualizado));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<UsuarioResponseDTO>> actualizarEstadoPatch(
            @PathVariable Long id,
            @RequestParam String status) {
        UsuarioResponseDTO actualizado = usuarioService.actualizarEstado(id, status);
        return ResponseEntity.ok(ApiResponse.ok("Estado actualizado correctamente", actualizado));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> eliminarUsuario(@PathVariable Long id) {
        usuarioService.eliminarUsuario(id);
        return ResponseEntity.ok(ApiResponse.ok("Usuario eliminado correctamente", null));
    }
}
