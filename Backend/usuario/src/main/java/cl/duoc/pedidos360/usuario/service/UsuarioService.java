package cl.duoc.pedidos360.usuario.service;

import cl.duoc.pedidos360.usuario.dto.AdminBootstrapDTO;
import cl.duoc.pedidos360.usuario.dto.AuthRequest;
import cl.duoc.pedidos360.usuario.dto.AuthResponse;
import cl.duoc.pedidos360.usuario.dto.UsuarioCreateDTO;
import cl.duoc.pedidos360.usuario.dto.UsuarioResponseDTO;
import cl.duoc.pedidos360.usuario.entity.Usuario;
import cl.duoc.pedidos360.usuario.enums.Rol;
import cl.duoc.pedidos360.usuario.repository.UsuarioRepository;
import cl.duoc.pedidos360.usuario.security.JwtUtil;
import cl.duoc.pedidos360.usuario.security.LoginAttemptService;
import cl.duoc.pedidos360.usuario.security.TurnstileVerificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cl.duoc.pedidos360.usuario.config.RabbitMQConfig;
import cl.duoc.pedidos360.usuario.event.UsuarioLoginExitosoEvent;
import cl.duoc.pedidos360.usuario.event.UsuarioLoginFallidoEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UsuarioService {

    private static final Logger log = LoggerFactory.getLogger(UsuarioService.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RabbitTemplate rabbitTemplate;
    private final org.springframework.core.env.Environment environment;
    private final TurnstileVerificationService turnstileService;
    private final LoginAttemptService loginAttemptService;

    @Autowired
    public UsuarioService(UsuarioRepository usuarioRepository,
                          PasswordEncoder passwordEncoder,
                          JwtUtil jwtUtil,
                          @Autowired(required = false) RabbitTemplate rabbitTemplate,
                          @Autowired(required = false) org.springframework.core.env.Environment environment,
                          @Autowired(required = false) TurnstileVerificationService turnstileService,
                          @Autowired(required = false) LoginAttemptService loginAttemptService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.rabbitTemplate = rabbitTemplate;
        this.environment = environment;
        this.turnstileService = turnstileService;
        this.loginAttemptService = loginAttemptService;
    }

    public UsuarioService(UsuarioRepository usuarioRepository,
                          PasswordEncoder passwordEncoder,
                          JwtUtil jwtUtil,
                          RabbitTemplate rabbitTemplate,
                          org.springframework.core.env.Environment environment) {
        this(usuarioRepository, passwordEncoder, jwtUtil, rabbitTemplate, environment, null, null);
    }

    @Transactional
    public UsuarioResponseDTO bootstrapAdmin(AdminBootstrapDTO dto) {
        log.info("[USER-SERVICE] Solicitud de bootstrap de administrador inicial para: {}", dto.getEmail());
        long adminCount = usuarioRepository.countByRol(Rol.ADMIN);
        if (adminCount > 0) {
            log.warn("[USER-SERVICE] Bootstrap rechazado: ya existe al menos un administrador en el sistema");
            throw new IllegalStateException("El sistema ya cuenta con un administrador registrado");
        }

        String expectedToken = environment != null ? environment.getProperty("ADMIN_BOOTSTRAP_TOKEN") : null;
        if (expectedToken != null && !expectedToken.isBlank()) {
            if (dto.getBootstrapToken() == null || !expectedToken.equals(dto.getBootstrapToken().trim())) {
                throw new SecurityException("Token de bootstrap administrativo inválido o no suministrado");
            }
        }

        if (usuarioRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new IllegalArgumentException("El email ya se encuentra registrado");
        }

        if (dto.getPassword() == null || dto.getPassword().length() < 12) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 12 caracteres");
        }

        Usuario admin = new Usuario(
                null,
                dto.getNombre(),
                dto.getEmail(),
                passwordEncoder.encode(dto.getPassword()),
                Rol.ADMIN,
                dto.getTelefono(),
                "Dirección Central Pedidos360",
                "FULL_TIME",
                "ACTIVO"
        );

        Usuario guardado = usuarioRepository.save(admin);
        log.info("[USER-SERVICE] Administrador inicial creado exitosamente ID={} Email={}", guardado.getId(), guardado.getEmail());
        return UsuarioResponseDTO.fromEntity(guardado);
    }

    @Transactional(readOnly = true)
    public AuthResponse autenticar(AuthRequest request) {
        log.info("[USER-SERVICE] Login attempt for: {}", request.getEmail());

        if (loginAttemptService != null && loginAttemptService.isBlocked(request.getEmail())) {
            log.warn("[USER-SERVICE] Login bloqueado temporalmente por intentos fallidos: {}", request.getEmail());
            throw new RuntimeException("Cuenta bloqueada temporalmente por múltiples intentos fallidos. Intente más tarde.");
        }

        if (turnstileService != null && StringUtils.hasText(request.getTurnstileToken())) {
            boolean validCaptcha = turnstileService.verifyToken(request.getTurnstileToken(), null);
            if (!validCaptcha) {
                log.warn("[USER-SERVICE] Falló validación Turnstile para: {}", request.getEmail());
                throw new RuntimeException("Verificación de seguridad Turnstile inválida o expirada");
            }
        }

        Usuario usuario = usuarioRepository.findByEmail(request.getEmail()).orElse(null);

        if (usuario == null) {
            if (loginAttemptService != null) {
                loginAttemptService.loginFailed(request.getEmail());
            }
            publicarLoginFallido(request.getEmail(), "Usuario no encontrado");
            throw new RuntimeException("Credenciales inválidas: Usuario no encontrado");
        }

        if (!passwordEncoder.matches(request.getPassword(), usuario.getPasswordHash())) {
            if (loginAttemptService != null) {
                loginAttemptService.loginFailed(request.getEmail());
            }
            log.warn("[USER-SERVICE] Invalid password attempt for: {}", request.getEmail());
            publicarLoginFallido(request.getEmail(), "Contraseña incorrecta");
            throw new RuntimeException("Credenciales inválidas: Contraseña incorrecta");
        }

        if (loginAttemptService != null) {
            loginAttemptService.loginSucceeded(request.getEmail());
        }

        String token = jwtUtil.generateToken(usuario.getEmail(), usuario.getRol());
        log.info("[USER-SERVICE] Login successful for: {} ID={}", usuario.getEmail(), usuario.getId());
        publicarLoginExitoso(usuario);
        return new AuthResponse(token, usuario.getId(), usuario.getNombre(), usuario.getEmail(), usuario.getRol());
    }

    private void publicarLoginExitoso(Usuario usuario) {
        if (rabbitTemplate == null) return;
        try {
            UsuarioLoginExitosoEvent event = new UsuarioLoginExitosoEvent(
                    usuario.getId(),
                    usuario.getEmail(),
                    usuario.getRol() != null ? usuario.getRol().name() : "CLIENTE"
            );
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_EVENTS, RabbitMQConfig.ROUTING_KEY_LOGIN_EXITOSO, event);
            log.info("[USER-SERVICE] Published login success event: eventId={} email={}", event.getEventId(), event.getEmail());
        } catch (Exception ex) {
            log.warn("[USER-SERVICE] Failed to publish login success event for email={}: {}", usuario.getEmail(), ex.getMessage());
        }
    }

    private void publicarLoginFallido(String email, String motivo) {
        if (rabbitTemplate == null) return;
        try {
            UsuarioLoginFallidoEvent event = new UsuarioLoginFallidoEvent(email, motivo);
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_EVENTS, RabbitMQConfig.ROUTING_KEY_LOGIN_FALLIDO, event);
            log.info("[USER-SERVICE] Published login failure event: eventId={} email={}", event.getEventId(), email);
        } catch (Exception ex) {
            log.warn("[USER-SERVICE] Failed to publish login failure event for email={}: {}", email, ex.getMessage());
        }
    }

    /**
     * Provisioning JIT para usuarios que inician sesión con Microsoft Entra ID.
     */
    @Transactional
    public UsuarioResponseDTO sincronizarUsuarioExterno(String email, String nombre, String rolNombre) {
        Rol rol = parseRol(rolNombre);
        Usuario usuario = usuarioRepository.findByEmail(email).orElse(null);

        if (usuario == null) {
            usuario = new Usuario(
                    null,
                    (nombre != null && !nombre.isBlank()) ? nombre : email,
                    email,
                    passwordEncoder.encode(java.util.UUID.randomUUID().toString()),
                    rol
            );
            usuario.setEstado("ACTIVO");
            usuario.setTurno("FULL_TIME");
            log.info("[USER-SERVICE] Provisioning JIT (Entra ID) — nuevo usuario: {}", email);
        } else {
            usuario.setRol(rol);
            if (nombre != null && !nombre.isBlank()) {
                usuario.setNombre(nombre);
            }
            log.info("[USER-SERVICE] Provisioning JIT (Entra ID) — usuario existente: {} ID={}", email, usuario.getId());
        }

        return UsuarioResponseDTO.fromEntity(usuarioRepository.save(usuario));
    }

    private Rol parseRol(String nombre) {
        if (nombre == null) return Rol.CLIENTE;
        try {
            return Rol.valueOf(nombre.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Rol.CLIENTE;
        }
    }

    @Transactional
    public UsuarioResponseDTO crearUsuario(UsuarioCreateDTO dto) {
        log.info("[USER-SERVICE] Creating user: {}", dto.getEmail());

        if (turnstileService != null && StringUtils.hasText(dto.getTurnstileToken())) {
            boolean validCaptcha = turnstileService.verifyToken(dto.getTurnstileToken(), null);
            if (!validCaptcha) {
                log.warn("[USER-SERVICE] Falló validación Turnstile en registro para: {}", dto.getEmail());
                throw new RuntimeException("Verificación de seguridad Turnstile inválida o expirada");
            }
        }

        if (usuarioRepository.findByEmail(dto.getEmail()).isPresent()) {
            log.warn("[USER-SERVICE] Duplicate email creation attempt: {}", dto.getEmail());
            throw new RuntimeException("El email ya se encuentra registrado");
        }

        // Prevención de elevación de privilegios: no permitir registrarse como ADMIN de forma directa
        Rol rolAsignado = (dto.getRol() != null && dto.getRol() != Rol.ADMIN) ? dto.getRol() : Rol.CLIENTE;
        String rawPassword = (dto.getPassword() != null && !dto.getPassword().isBlank())
                ? dto.getPassword()
                : java.util.UUID.randomUUID().toString();

        Usuario usuario = new Usuario(
                null,
                dto.getNombre(),
                dto.getEmail(),
                passwordEncoder.encode(rawPassword),
                rolAsignado,
                dto.getTelefono(),
                dto.getDireccion(),
                dto.getTurno() != null ? dto.getTurno() : "FULL_TIME",
                dto.getEstado() != null ? dto.getEstado() : "ACTIVO"
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

    @Transactional(readOnly = true)
    public UsuarioResponseDTO obtenerPorEmail(String email) {
        log.info("[USER-SERVICE] Fetching user by email={}", email);
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con email: " + email));
        return UsuarioResponseDTO.fromEntity(usuario);
    }

    @Transactional
    public UsuarioResponseDTO actualizarUsuario(Long id, UsuarioCreateDTO dto) {
        log.info("[USER-SERVICE] Updating user ID={}", id);
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + id));

        if (dto.getEmail() != null && !dto.getEmail().isBlank()) {
            if (!usuario.getEmail().equalsIgnoreCase(dto.getEmail()) &&
                    usuarioRepository.findByEmail(dto.getEmail()).isPresent()) {
                throw new RuntimeException("El email ya se encuentra registrado");
            }
            usuario.setEmail(dto.getEmail());
        }

        if (dto.getNombre() != null && !dto.getNombre().isBlank()) {
            usuario.setNombre(dto.getNombre());
        }
        if (dto.getRol() != null) {
            usuario.setRol(dto.getRol());
        }
        if (dto.getTelefono() != null) {
            usuario.setTelefono(dto.getTelefono());
        }
        if (dto.getDireccion() != null) {
            usuario.setDireccion(dto.getDireccion());
        }
        if (dto.getTurno() != null && !dto.getTurno().isBlank()) {
            usuario.setTurno(dto.getTurno());
        }
        if (dto.getEstado() != null && !dto.getEstado().isBlank()) {
            usuario.setEstado(dto.getEstado());
        }
        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            usuario.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        }

        Usuario actualizado = usuarioRepository.save(usuario);
        log.info("[USER-SERVICE] User updated successfully ID={}", actualizado.getId());
        return UsuarioResponseDTO.fromEntity(actualizado);
    }

    @Transactional
    public void cambiarPassword(Long id, String currentPassword, String newPassword) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + id));
        if (!passwordEncoder.matches(currentPassword, usuario.getPasswordHash())) {
            throw new IllegalArgumentException("La contraseña actual es incorrecta");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("La nueva contraseña debe tener al menos 6 caracteres");
        }
        usuario.setPasswordHash(passwordEncoder.encode(newPassword));
        usuarioRepository.save(usuario);
        log.info("[USER-SERVICE] Password changed successfully for user ID={}", id);
    }

    @Transactional
    public void cambiarPasswordPorEmail(String email, String currentPassword, String newPassword) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con email: " + email));
        if (!passwordEncoder.matches(currentPassword, usuario.getPasswordHash())) {
            throw new IllegalArgumentException("La contraseña actual es incorrecta");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("La nueva contraseña debe tener al menos 6 caracteres");
        }
        usuario.setPasswordHash(passwordEncoder.encode(newPassword));
        usuarioRepository.save(usuario);
        log.info("[USER-SERVICE] Password changed successfully for user email={}", email);
    }

    @Transactional
    public UsuarioResponseDTO actualizarEstado(Long id, String nuevoEstado) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado con ID: " + id));
        usuario.setEstado(nuevoEstado);
        Usuario guardado = usuarioRepository.save(usuario);
        log.info("[USER-SERVICE] User ID={} status updated to {}", id, nuevoEstado);
        return UsuarioResponseDTO.fromEntity(guardado);
    }

    @Transactional
    public void solicitarRecuperacionPassword(String email) {
        log.info("[USER-SERVICE] Password reset requested for email: {}", email);
        java.util.Optional<Usuario> usuarioOpt = usuarioRepository.findByEmail(email);

        if (usuarioOpt.isEmpty()) {
            log.info("[USER-SERVICE] Password reset requested for non-existent email: {}", email);
            return;
        }

        Usuario usuario = usuarioOpt.get();
        String resetToken = java.util.UUID.randomUUID().toString();

        if (rabbitTemplate != null) {
            try {
                cl.duoc.pedidos360.usuario.event.PasswordResetSolicitadoEvent event =
                        new cl.duoc.pedidos360.usuario.event.PasswordResetSolicitadoEvent(
                                usuario.getId(),
                                usuario.getEmail(),
                                resetToken
                        );
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.EXCHANGE_EVENTS,
                        RabbitMQConfig.ROUTING_KEY_PASSWORD_RESET,
                        event
                );
                log.info("[USER-SERVICE] Published password reset event: eventId={} email={}", event.getEventId(), email);
            } catch (Exception ex) {
                log.warn("[USER-SERVICE] Failed to publish password reset event for email={}: {}", email, ex.getMessage());
            }
        }
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
