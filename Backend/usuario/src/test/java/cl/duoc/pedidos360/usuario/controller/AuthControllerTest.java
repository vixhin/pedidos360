package cl.duoc.pedidos360.usuario.controller;

import cl.duoc.pedidos360.usuario.dto.*;
import cl.duoc.pedidos360.usuario.entity.Usuario;
import cl.duoc.pedidos360.usuario.enums.Rol;
import cl.duoc.pedidos360.usuario.security.JwtUtil;
import cl.duoc.pedidos360.usuario.service.SessionService;
import cl.duoc.pedidos360.usuario.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private UsuarioService usuarioService;

    @Mock
    private SessionService sessionService;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthController authController;

    private Usuario mockUsuario;
    private UsuarioResponseDTO mockUserDto;

    @BeforeEach
    void setUp() {
        mockUsuario = new Usuario(1L, "Admin", "admin@pedidos360.cl", "hashedPass", Rol.ADMIN);
        mockUserDto = new UsuarioResponseDTO(1L, "Admin", "admin@pedidos360.cl", Rol.ADMIN, null, null);
    }

    @Test
    @DisplayName("login - retorna HTTP 200 y establece cookies HttpOnly")
    void testLoginExito() {
        AuthRequest request = new AuthRequest("admin@pedidos360.cl", "password");
        when(usuarioService.autenticarUsuario(anyString(), anyString(), any(), any())).thenReturn(mockUsuario);
        when(sessionService.crearSesion(anyLong(), any(), any()))
                .thenReturn(new SessionService.SessionCreationResult("sid-123", "refresh-token-xyz", LocalDateTime.now().plusHours(8)));
        when(jwtUtil.getExpirationMs()).thenReturn(900_000L);
        when(jwtUtil.generateToken(anyLong(), anyString(), any(Rol.class), anyString())).thenReturn("jwt-token-abc");

        ResponseEntity<ApiResponse<AuthResponse>> response = authController.login(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getEmail()).isEqualTo("admin@pedidos360.cl");
        assertThat(response.getHeaders().get("Set-Cookie")).isNotEmpty();
    }

    @Test
    @DisplayName("register - retorna HTTP 201 al registrar cliente")
    void testRegisterExito() {
        UsuarioCreateDTO dto = new UsuarioCreateDTO("Cliente Test", "cliente@pedidos360.cl", "password123", Rol.CLIENTE);
        when(usuarioService.registrarCliente(any(UsuarioCreateDTO.class), any())).thenReturn(mockUserDto);

        ResponseEntity<ApiResponse<UsuarioResponseDTO>> response = authController.register(dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getEmail()).isEqualTo("admin@pedidos360.cl");
    }

    @Test
    @DisplayName("bootstrapAdmin - retorna HTTP 201 al crear administrador inicial")
    void testBootstrapAdminExito() {
        AdminBootstrapDTO dto = new AdminBootstrapDTO("Admin", "admin@pedidos360.cl", "SuperSecurePassword123!", "token32chars", "+56912345678");
        when(usuarioService.bootstrapAdmin(any(AdminBootstrapDTO.class))).thenReturn(mockUserDto);

        ResponseEntity<ApiResponse<UsuarioResponseDTO>> response = authController.bootstrapAdmin(dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getData().getEmail()).isEqualTo("admin@pedidos360.cl");
    }

    @Test
    @DisplayName("logout - retorna HTTP 200 y limpia cookies de sesión")
    void testLogoutExito() {
        ResponseEntity<ApiResponse<Void>> response = authController.logout();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().get("Set-Cookie")).isNotEmpty();
    }
}
