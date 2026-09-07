package cl.duoc.pedidos360.usuario.controller;

import cl.duoc.pedidos360.usuario.dto.ApiResponse;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsuarioControllerTest {

    @Mock
    private UsuarioService usuarioService;

    @InjectMocks
    private UsuarioController usuarioController;

    private UsuarioResponseDTO mockUserDto;

    @BeforeEach
    void setUp() {
        mockUserDto = new UsuarioResponseDTO(1L, "Test", "test@pedidos360.cl", Rol.CLIENTE, null, null);
    }

    @Test
    @DisplayName("crearUsuario - retorna HTTP 201 CREATED")
    void testCrearUsuario() {
        UsuarioCreateDTO dto = new UsuarioCreateDTO("Test", "test@pedidos360.cl", "pass", Rol.CLIENTE);
        when(usuarioService.crearUsuario(any(UsuarioCreateDTO.class))).thenReturn(mockUserDto);

        ResponseEntity<ApiResponse<UsuarioResponseDTO>> response = usuarioController.crearUsuario(dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("listarUsuarios - retorna HTTP 200 OK con la lista")
    void testListarUsuarios() {
        when(usuarioService.obtenerTodos()).thenReturn(List.of(mockUserDto));

        ResponseEntity<ApiResponse<List<UsuarioResponseDTO>>> response = usuarioController.listarUsuarios();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).hasSize(1);
    }

    @Test
    @DisplayName("obtenerPorId - retorna HTTP 200 OK con el usuario")
    void testObtenerPorId() {
        when(usuarioService.obtenerPorId(1L)).thenReturn(mockUserDto);

        ResponseEntity<ApiResponse<UsuarioResponseDTO>> response = usuarioController.obtenerPorId(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("actualizarUsuario - retorna HTTP 200 OK con datos actualizados")
    void testActualizarUsuario() {
        UsuarioCreateDTO dto = new UsuarioCreateDTO("Updated", "test@pedidos360.cl", "pass", Rol.CLIENTE);
        when(usuarioService.actualizarUsuario(eq(1L), any(UsuarioCreateDTO.class))).thenReturn(mockUserDto);

        ResponseEntity<ApiResponse<UsuarioResponseDTO>> response = usuarioController.actualizarUsuario(1L, dto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("eliminarUsuario - retorna HTTP 200 OK")
    void testEliminarUsuario() {
        doNothing().when(usuarioService).eliminarUsuario(1L);

        ResponseEntity<ApiResponse<Void>> response = usuarioController.eliminarUsuario(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
