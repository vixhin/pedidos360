package cl.duoc.pedidos360.usuario.controller;

import cl.duoc.pedidos360.usuario.dto.ApiResponse;
import cl.duoc.pedidos360.usuario.dto.AuthRequest;
import cl.duoc.pedidos360.usuario.dto.AuthResponse;
import cl.duoc.pedidos360.usuario.dto.UsuarioCreateDTO;
import cl.duoc.pedidos360.usuario.dto.UsuarioResponseDTO;
import cl.duoc.pedidos360.usuario.service.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class AuthController {

    private final UsuarioService usuarioService;

    public AuthController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody AuthRequest request) {
        AuthResponse response = usuarioService.autenticar(request);
        return ResponseEntity.ok(ApiResponse.ok("Autenticación exitosa", response));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UsuarioResponseDTO>> register(@Valid @RequestBody UsuarioCreateDTO request) {
        UsuarioResponseDTO response = usuarioService.crearUsuario(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Usuario registrado correctamente", response));
    }
}
