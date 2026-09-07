package cl.duoc.pedidos360.usuario.controller;

import cl.duoc.pedidos360.usuario.dto.ApiResponse;
import cl.duoc.pedidos360.usuario.dto.AuthRequest;
import cl.duoc.pedidos360.usuario.dto.AuthResponse;
import cl.duoc.pedidos360.usuario.dto.UsuarioCreateDTO;
import cl.duoc.pedidos360.usuario.dto.UsuarioResponseDTO;
import cl.duoc.pedidos360.usuario.enums.Rol;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private UsuarioService usuarioService;

    @InjectMocks
    private AuthController authController;

    private AuthResponse mockAuthResponse;
    private UsuarioResponseDTO mockUserDto;

    @BeforeEach
    void setUp() {
        mockAuthResponse = new AuthResponse("token-123", 1L, "Admin", "admin@pedidos360.cl", Rol.ADMIN);
        mockUserDto = new UsuarioResponseDTO(1L, "Admin", "admin@pedidos360.cl", Rol.ADMIN, null, null);
    }

    @Test
    @DisplayName("login - retorna HTTP 200 y respuesta exitosa")
    void testLoginExito() {
        AuthRequest request = new AuthRequest("admin@pedidos360.cl", "password");
        when(usuarioService.autenticar(any(AuthRequest.class))).thenReturn(mockAuthResponse);

        ResponseEntity<ApiResponse<AuthResponse>> response = authController.login(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getToken()).isEqualTo("token-123");
    }

    @Test
    @DisplayName("register - retorna HTTP 201 al registrar usuario")
    void testRegisterExito() {
        UsuarioCreateDTO dto = new UsuarioCreateDTO("Admin", "admin@pedidos360.cl", "pass", Rol.ADMIN);
        when(usuarioService.crearUsuario(any(UsuarioCreateDTO.class))).thenReturn(mockUserDto);

        ResponseEntity<ApiResponse<UsuarioResponseDTO>> response = authController.register(dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getEmail()).isEqualTo("admin@pedidos360.cl");
    }
}
