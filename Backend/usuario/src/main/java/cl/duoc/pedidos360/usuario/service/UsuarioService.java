package cl.duoc.pedidos360.usuario.service;

import cl.duoc.pedidos360.usuario.dto.AuthRequest;
import cl.duoc.pedidos360.usuario.dto.AuthResponse;
import cl.duoc.pedidos360.usuario.dto.UsuarioCreateDTO;
import cl.duoc.pedidos360.usuario.dto.UsuarioResponseDTO;
import cl.duoc.pedidos360.usuario.entity.Usuario;
import cl.duoc.pedidos360.usuario.enums.Rol;
import cl.duoc.pedidos360.usuario.repository.UsuarioRepository;
import cl.duoc.pedidos360.usuario.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UsuarioService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public UsuarioService(UsuarioRepository usuarioRepository,
                          PasswordEncoder passwordEncoder,
                          JwtUtil jwtUtil) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initDefaultUsers() {
        if (usuarioRepository.findByEmail("admin@pedidos360.cl").isEmpty()) {
            Usuario admin = new Usuario(
                    null,
                    "Administrador Vixo",
                    "admin@pedidos360.cl",
                    passwordEncoder.encode("chupalovixo"),
                    Rol.ADMIN
            );
            usuarioRepository.save(admin);
            log.info("[USER-SERVICE] Seeded mock admin user: admin@pedidos360.cl");
        }
    }

    @Transactional(readOnly = true)
    public AuthResponse autenticar(AuthRequest request) {
        log.info("[USER-SERVICE] Login attempt for: {}", request.getEmail());
        Usuario usuario = usuarioRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Credenciales inválidas: Usuario no encontrado"));

        if (!passwordEncoder.matches(request.getPassword(), usuario.getPasswordHash())) {
            log.warn("[USER-SERVICE] Invalid password attempt for: {}", request.getEmail());
            throw new RuntimeException("Credenciales inválidas: Contraseña incorrecta");
        }

        String token = jwtUtil.generateToken(usuario.getEmail(), usuario.getRol());
        log.info("[USER-SERVICE] Login successful for: {} ID={}", usuario.getEmail(), usuario.getId());
        return new AuthResponse(token, usuario.getId(), usuario.getNombre(), usuario.getEmail(), usuario.getRol());
    }

    @Transactional
    public UsuarioResponseDTO crearUsuario(UsuarioCreateDTO dto) {
        log.info("[USER-SERVICE] Creating user: {}", dto.getEmail());
        if (usuarioRepository.findByEmail(dto.getEmail()).isPresent()) {
            log.warn("[USER-SERVICE] Duplicate email creation attempt: {}", dto.getEmail());
            throw new RuntimeException("El email ya se encuentra registrado");
        }

        Rol rolAsignado = (dto.getRol() != null) ? dto.getRol() : Rol.CLIENTE;

        Usuario usuario = new Usuario(
                null,
                dto.getNombre(),
                dto.getEmail(),
                passwordEncoder.encode(dto.getPassword()),
                rolAsignado
        );

        Usuario guardado = usuarioRepository.save(usuario);
        log.info("[USER-SERVICE] User created successfully ID={} Email={}", guardado.getId(), guardado.getEmail());
        return UsuarioResponseDTO.fromEntity(guardado);
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponseDTO> obtenerTodos() {
        log.info("[USER-SERVICE] Listing all users");
        return usuarioRepository.findAll().stream()
                .map(UsuarioResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UsuarioResponseDTO obtenerPorId(Long id) {
        log.info("[USER-SERVICE] Fetching user by ID={}", id);
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + id));
        return UsuarioResponseDTO.fromEntity(usuario);
    }

    @Transactional
    public UsuarioResponseDTO actualizarUsuario(Long id, UsuarioCreateDTO dto) {
        log.info("[USER-SERVICE] Updating user ID={}", id);
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + id));

        if (!usuario.getEmail().equalsIgnoreCase(dto.getEmail()) &&
                usuarioRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new RuntimeException("El email ya se encuentra registrado");
        }

        usuario.setNombre(dto.getNombre());
        usuario.setEmail(dto.getEmail());
        if (dto.getRol() != null) {
            usuario.setRol(dto.getRol());
        }
        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            usuario.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        }

        Usuario actualizado = usuarioRepository.save(usuario);
        log.info("[USER-SERVICE] User updated successfully ID={}", actualizado.getId());
        return UsuarioResponseDTO.fromEntity(actualizado);
    }

    @Transactional
    public void eliminarUsuario(Long id) {
        log.info("[USER-SERVICE] Deleting user ID={}", id);
        if (!usuarioRepository.existsById(id)) {
            throw new RuntimeException("Usuario no encontrado con ID: " + id);
        }
        usuarioRepository.deleteById(id);
        log.info("[USER-SERVICE] User deleted successfully ID={}", id);
    }
}
