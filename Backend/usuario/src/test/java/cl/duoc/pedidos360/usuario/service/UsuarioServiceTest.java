package cl.duoc.pedidos360.usuario.service;

import cl.duoc.pedidos360.usuario.dto.AuthRequest;
import cl.duoc.pedidos360.usuario.dto.AuthResponse;
import cl.duoc.pedidos360.usuario.dto.UsuarioCreateDTO;
import cl.duoc.pedidos360.usuario.dto.UsuarioResponseDTO;
import cl.duoc.pedidos360.usuario.entity.Usuario;
import cl.duoc.pedidos360.usuario.enums.Rol;
import cl.duoc.pedidos360.usuario.repository.UsuarioRepository;
import cl.duoc.pedidos360.usuario.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import cl.duoc.pedidos360.usuario.config.RabbitMQConfig;
import cl.duoc.pedidos360.usuario.event.UsuarioLoginExitosoEvent;
import cl.duoc.pedidos360.usuario.event.UsuarioLoginFallidoEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private UsuarioService usuarioService;

    private Usuario mockUsuario;

    @BeforeEach
    void setUp() {
        mockUsuario = new Usuario(1L, "Test User", "test@pedidos360.cl", "encoded_password", Rol.CLIENTE);
    }

    @Test
    @DisplayName("autenticar - éxito con credenciales válidas y publica evento")
    void testAutenticarExito() {
        AuthRequest request = new AuthRequest("test@pedidos360.cl", "password123");
        when(usuarioRepository.findByEmail("test@pedidos360.cl")).thenReturn(Optional.of(mockUsuario));
        when(passwordEncoder.matches("password123", "encoded_password")).thenReturn(true);
        when(jwtUtil.generateToken("test@pedidos360.cl", Rol.CLIENTE)).thenReturn("fake-jwt-token");

        AuthResponse response = usuarioService.autenticar(request);

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("fake-jwt-token");
        assertThat(response.getEmail()).isEqualTo("test@pedidos360.cl");
        assertThat(response.getRol()).isEqualTo(Rol.CLIENTE);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_EVENTS),
                eq(RabbitMQConfig.ROUTING_KEY_LOGIN_EXITOSO),
                any(UsuarioLoginExitosoEvent.class)
        );
    }

    @Test
    @DisplayName("autenticar - lanza excepción por usuario no encontrado y publica evento fallido")
    void testAutenticarUsuarioNoEncontrado() {
        AuthRequest request = new AuthRequest("desconocido@pedidos360.cl", "pass");
        when(usuarioRepository.findByEmail("desconocido@pedidos360.cl")).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> usuarioService.autenticar(request));

        assertThat(exception.getMessage()).contains("Credenciales inválidas");
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_EVENTS),
                eq(RabbitMQConfig.ROUTING_KEY_LOGIN_FALLIDO),
                any(UsuarioLoginFallidoEvent.class)
        );
    }

    @Test
    @DisplayName("autenticar - lanza excepción por contraseña incorrecta y publica evento fallido")
    void testAutenticarPasswordIncorrecta() {
        AuthRequest request = new AuthRequest("test@pedidos360.cl", "wrongpass");
        when(usuarioRepository.findByEmail("test@pedidos360.cl")).thenReturn(Optional.of(mockUsuario));
        when(passwordEncoder.matches("wrongpass", "encoded_password")).thenReturn(false);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> usuarioService.autenticar(request));

        assertThat(exception.getMessage()).contains("Contraseña incorrecta");
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_EVENTS),
                eq(RabbitMQConfig.ROUTING_KEY_LOGIN_FALLIDO),
                any(UsuarioLoginFallidoEvent.class)
        );
    }

    @Test
    @DisplayName("crearUsuario - éxito al registrar nuevo usuario")
    void testCrearUsuarioExito() {
        UsuarioCreateDTO dto = new UsuarioCreateDTO("Nuevo User", "nuevo@pedidos360.cl", "pass123", Rol.CLIENTE);
        when(usuarioRepository.findByEmail("nuevo@pedidos360.cl")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("pass123")).thenReturn("encoded_pass123");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> {
            Usuario u = i.getArgument(0);
            u.setId(2L);
            return u;
        });

        UsuarioResponseDTO response = usuarioService.crearUsuario(dto);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(2L);
        assertThat(response.getEmail()).isEqualTo("nuevo@pedidos360.cl");
        verify(usuarioRepository).save(any(Usuario.class));
    }

    @Test
    @DisplayName("crearUsuario - lanza excepción por email duplicado")
    void testCrearUsuarioEmailDuplicado() {
        UsuarioCreateDTO dto = new UsuarioCreateDTO("Test", "test@pedidos360.cl", "pass", Rol.CLIENTE);
        when(usuarioRepository.findByEmail("test@pedidos360.cl")).thenReturn(Optional.of(mockUsuario));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> usuarioService.crearUsuario(dto));

        assertThat(exception.getMessage()).contains("email ya se encuentra registrado");
    }

    @Test
    @DisplayName("obtenerTodos - retorna lista de usuarios")
    void testObtenerTodos() {
        when(usuarioRepository.findAll()).thenReturn(List.of(mockUsuario));

        List<UsuarioResponseDTO> usuarios = usuarioService.obtenerTodos();

        assertThat(usuarios).hasSize(1);
        assertThat(usuarios.get(0).getEmail()).isEqualTo("test@pedidos360.cl");
    }

    @Test
    @DisplayName("obtenerPorId - retorna usuario existente")
    void testObtenerPorIdExito() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(mockUsuario));

        UsuarioResponseDTO dto = usuarioService.obtenerPorId(1L);

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("obtenerPorId - lanza excepción cuando no existe")
    void testObtenerPorIdNoEncontrado() {
        when(usuarioRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> usuarioService.obtenerPorId(99L));
    }

    @Test
    @DisplayName("actualizarUsuario - modifica datos correctamente")
    void testActualizarUsuarioExito() {
        UsuarioCreateDTO dto = new UsuarioCreateDTO("Updated Name", "test@pedidos360.cl", "newpass", Rol.VENDEDOR);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(mockUsuario));
        when(passwordEncoder.encode("newpass")).thenReturn("encoded_newpass");
        when(usuarioRepository.save(any(Usuario.class))).thenReturn(mockUsuario);

        UsuarioResponseDTO response = usuarioService.actualizarUsuario(1L, dto);

        assertThat(response).isNotNull();
        verify(usuarioRepository).save(mockUsuario);
    }

    @Test
    @DisplayName("eliminarUsuario - elimina usuario existente")
    void testEliminarUsuarioExito() {
        when(usuarioRepository.existsById(1L)).thenReturn(true);
        doNothing().when(usuarioRepository).deleteById(1L);

        usuarioService.eliminarUsuario(1L);

        verify(usuarioRepository).deleteById(1L);
    }

    @Test
    @DisplayName("eliminarUsuario - lanza excepción si no existe")
    void testEliminarUsuarioNoExiste() {
        when(usuarioRepository.existsById(99L)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> usuarioService.eliminarUsuario(99L));
    }

    @Test
    @DisplayName("initDefaultUsers - ejecuta seed si repositorio está vacío")
    void testInitDefaultUsers() {
        when(usuarioRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(usuarioRepository.save(any(Usuario.class))).thenReturn(mockUsuario);

        usuarioService.initDefaultUsers();

        verify(usuarioRepository, times(5)).save(any(Usuario.class));
    }

    @Test
    @DisplayName("solicitarRecuperacionPassword - genera token y publica evento usuario.password.reset")
    void testSolicitarRecuperacionPasswordPublicaEvento() {
        when(usuarioRepository.findByEmail("test@pedidos360.cl")).thenReturn(Optional.of(mockUsuario));

        String token = usuarioService.solicitarRecuperacionPassword("test@pedidos360.cl");

        assertThat(token).isNotBlank();
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE_EVENTS),
                eq(RabbitMQConfig.ROUTING_KEY_PASSWORD_RESET),
                any(cl.duoc.pedidos360.usuario.event.PasswordResetSolicitadoEvent.class)
        );
    }
}
