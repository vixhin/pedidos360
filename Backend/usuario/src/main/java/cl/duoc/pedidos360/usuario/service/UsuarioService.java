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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
    private final SessionService sessionService;

    @Value("${admin.bootstrap.enabled:false}")
    private boolean bootstrapEnabled;

    public void setBootstrapEnabled(boolean bootstrapEnabled) {
        this.bootstrapEnabled = bootstrapEnabled;
    }

    @Autowired
    public UsuarioService(UsuarioRepository usuarioRepository,
                          PasswordEncoder passwordEncoder,
                          JwtUtil jwtUtil,
                          @Autowired(required = false) RabbitTemplate rabbitTemplate,
                          @Autowired(required = false) org.springframework.core.env.Environment environment,
                          @Autowired(required = false) TurnstileVerificationService turnstileService,
                          @Autowired(required = false) LoginAttemptService loginAttemptService,
                          @Autowired(required = false) SessionService sessionService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.rabbitTemplate = rabbitTemplate;
        this.environment = environment;
        this.turnstileService = turnstileService;
        this.loginAttemptService = loginAttemptService;
        this.sessionService = sessionService;
    }

    @Transactional
    public UsuarioResponseDTO bootstrapAdmin(AdminBootstrapDTO dto) {
        log.info("[USER-SERVICE] Solicitud de bootstrap de administrador inicial");

        if (!bootstrapEnabled) {
            log.warn("[USER-SERVICE] Bootstrap rechazado: procedimiento deshabilitado por configuración.");
            throw new SecurityException("El procedimiento de bootstrap administrativo está deshabilitado.");
        }

        String expectedToken = environment != null ? environment.getProperty("ADMIN_BOOTSTRAP_TOKEN") : null;
        if (expectedToken == null || expectedToken.trim().length() < 32) {
            log.error("[USER-SERVICE] Bootstrap rechazado: ADMIN_BOOTSTRAP_TOKEN no configurado o longitud menor a 32 caracteres.");
            throw new SecurityException("Token de bootstrap administrativo no configurado de forma segura.");
        }

        if (dto.getBootstrapToken() == null ||
                !MessageDigest.isEqual(expectedToken.trim().getBytes(StandardCharsets.UTF_8),
                        dto.getBootstrapToken().trim().getBytes(StandardCharsets.UTF_8))) {
            log.warn("[USER-SERVICE] Bootstrap rechazado: token incorrecto.");
            throw new SecurityException("Token de bootstrap administrativo inválido.");
        }

        long adminCount = usuarioRepository.countByRol(Rol.ADMIN);
        if (adminCount > 0) {
            log.warn("[USER-SERVICE] Bootstrap rechazado: ya existe al menos un administrador en el sistema.");
            throw new IllegalStateException("El sistema ya cuenta con un administrador registrado. Bootstrap bloqueado.");
        }

        if (usuarioRepository.findByEmail(dto.getEmail()).isPresent()) {
            throw new IllegalArgumentException("El email ya se encuentra registrado.");
        }

        if (dto.getPassword() == null || dto.getPassword().length() < 12) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 12 caracteres.");
        }

        Usuario admin = new Usuario(
                null,
                dto.getNombre(),
                dto.getEmail().trim().toLowerCase(),
                passwordEncoder.encode(dto.getPassword()),
                Rol.ADMIN,
                dto.getTelefono(),
                "Dirección Central Pedidos360",
                "FULL_TIME",
                "ACTIVO"
        );

        Usuario guardado = usuarioRepository.save(admin);
        log.info("[USER-SERVICE] Administrador inicial creado exitosamente ID={}", guardado.getId());
        return UsuarioResponseDTO.fromEntity(guardado);
    }

    @Transactional(readOnly = true)
    public Usuario autenticarUsuario(String email, String password, String turnstileToken, String remoteIp) {
        log.info("[USER-SERVICE] Intento de login para email");

        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("Email y contraseña son obligatorios.");
        }

        String normalizedEmail = email.trim().toLowerCase();

        if (loginAttemptService != null && loginAttemptService.isBlocked(normalizedEmail)) {
            log.warn("[USER-SERVICE] Login bloqueado temporalmente por múltiples intentos fallidos.");
            throw new SecurityException("Cuenta bloqueada temporalmente por múltiples intentos fallidos. Intente más tarde.");
        }

        if (turnstileService != null) {
            boolean validCaptcha = turnstileService.verifyToken(turnstileToken, remoteIp, "login");
            if (!validCaptcha) {
                log.warn("[USER-SERVICE] Falló validación Turnstile en login.");
                throw new SecurityException("Verificación de seguridad Turnstile inválida o expirada.");
            }
        }

        Optional<Usuario> usuarioOpt = usuarioRepository.findByEmail(normalizedEmail);

        if (usuarioOpt.isEmpty()) {
            if (loginAttemptService != null) {
                loginAttemptService.loginFailed(normalizedEmail);
            }
            publicarLoginFallido(normalizedEmail, "Usuario no encontrado");
            throw new SecurityException("Credenciales inválidas.");
        }

        Usuario usuario = usuarioOpt.get();

        if (!passwordEncoder.matches(password, usuario.getPasswordHash())) {
            if (loginAttemptService != null) {
                loginAttemptService.loginFailed(normalizedEmail);
            }
            publicarLoginFallido(normalizedEmail, "Contraseña incorrecta");
            throw new SecurityException("Credenciales inválidas.");
        }

        if (!"ACTIVO".equalsIgnoreCase(usuario.getEstado())) {
            log.warn("[USER-SERVICE] Login rechazado para usuario inactivo ID={}", usuario.getId());
            throw new SecurityException("La cuenta de usuario se encuentra inactiva o suspendida.");
        }

        if (loginAttemptService != null) {
            loginAttemptService.loginSucceeded(normalizedEmail);
        }

        log.info("[USER-SERVICE] Login exitoso para ID={}", usuario.getId());
        publicarLoginExitoso(usuario);
        return usuario;
    }

    // Método legacy para retrocompatibilidad
    @Transactional(readOnly = true)
    public AuthResponse autenticar(AuthRequest request) {
        Usuario usuario = autenticarUsuario(request.getEmail(), request.getPassword(), request.getTurnstileToken(), null);
        String token = jwtUtil.generateToken(usuario.getId(), usuario.getEmail(), usuario.getRol(), java.util.UUID.randomUUID().toString());
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
        } catch (Exception ex) {
            log.warn("[USER-SERVICE] Fallo al publicar evento de login exitoso: {}", ex.getMessage());
        }
    }

    private void publicarLoginFallido(String email, String motivo) {
        if (rabbitTemplate == null) return;
        try {
            UsuarioLoginFallidoEvent event = new UsuarioLoginFallidoEvent(email, motivo);
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_EVENTS, RabbitMQConfig.ROUTING_KEY_LOGIN_FALLIDO, event);
        } catch (Exception ex) {
            log.warn("[USER-SERVICE] Fallo al publicar evento de login fallido: {}", ex.getMessage());
        }
    }

    @Transactional
    public UsuarioResponseDTO registrarCliente(UsuarioCreateDTO dto, String remoteIp) {
        log.info("[USER-SERVICE] Registro público de cliente nuevo");

        if (turnstileService != null) {
            boolean validCaptcha = turnstileService.verifyToken(dto.getTurnstileToken(), remoteIp, "register");
            if (!validCaptcha) {
                log.warn("[USER-SERVICE] Falló validación Turnstile en registro público.");
                throw new SecurityException("Verificación de seguridad Turnstile inválida o expirada.");
            }
        }

        if (dto.getEmail() == null || dto.getEmail().isBlank()) {
            throw new IllegalArgumentException("El email es obligatorio.");
        }

        String normalizedEmail = dto.getEmail().trim().toLowerCase();

        if (usuarioRepository.findByEmail(normalizedEmail).isPresent()) {
            log.warn("[USER-SERVICE] Intento de registro con email ya existente.");
            throw new IllegalArgumentException("El email ya se encuentra registrado en el sistema.");
        }

        if (dto.getPassword() == null || dto.getPassword().length() < 8) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 8 caracteres.");
        }

        // REGLA CRÍTICA DE SEGURIDAD: El registro público SIEMPRE asigna rol CLIENTE y estado ACTIVO
        Usuario usuario = new Usuario(
                null,
                dto.getNombre() != null ? dto.getNombre().trim() : "Cliente",
                normalizedEmail,
                passwordEncoder.encode(dto.getPassword()),
                Rol.CLIENTE,
                dto.getTelefono(),
                dto.getDireccion(),
                "FULL_TIME",
                "ACTIVO"
        );

        Usuario guardado = usuarioRepository.save(usuario);
        log.info("[USER-SERVICE] Cliente registrado correctamente ID={}", guardado.getId());
        return UsuarioResponseDTO.fromEntity(guardado);
    }

    // Creación administrativa (solo invocable por ADMIN)
    @Transactional
    public UsuarioResponseDTO crearUsuario(UsuarioCreateDTO dto) {
        log.info("[USER-SERVICE] Creación de usuario");

        if (dto.getEmail() == null || dto.getEmail().isBlank()) {
            throw new IllegalArgumentException("El email es obligatorio.");
        }

        String normalizedEmail = dto.getEmail().trim().toLowerCase();

        if (usuarioRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new IllegalArgumentException("El email ya se encuentra registrado.");
        }

        Rol rolAsignado = dto.getRol() != null ? dto.getRol() : Rol.CLIENTE;
        String rawPassword = (dto.getPassword() != null && !dto.getPassword().isBlank())
                ? dto.getPassword()
                : java.util.UUID.randomUUID().toString();

        Usuario usuario = new Usuario(
                null,
                dto.getNombre(),
                normalizedEmail,
                passwordEncoder.encode(rawPassword),
                rolAsignado,
                dto.getTelefono(),
                dto.getDireccion(),
                dto.getTurno() != null ? dto.getTurno() : "FULL_TIME",
                dto.getEstado() != null ? dto.getEstado() : "ACTIVO"
        );

        Usuario guardado = usuarioRepository.save(usuario);
        log.info("[USER-SERVICE] Usuario creado ID={} Rol={}", guardado.getId(), guardado.getRol());
        return UsuarioResponseDTO.fromEntity(guardado);
    }

    @Transactional
    public UsuarioResponseDTO sincronizarUsuarioExterno(String email, String nombre, String rolNombre, boolean rolFromAppRole) {
        String normalizedEmail = email.trim().toLowerCase();
        Rol rolSolicitado = parseRol(rolNombre);
        Usuario usuario = usuarioRepository.findByEmail(normalizedEmail).orElse(null);

        if (usuario == null) {
            // Usuario nuevo proveniente de Entra ID
            Rol rolInicial = rolFromAppRole ? rolSolicitado : Rol.CLIENTE;
            usuario = new Usuario(
                    null,
                    (nombre != null && !nombre.isBlank()) ? nombre : normalizedEmail,
                    normalizedEmail,
                    passwordEncoder.encode(java.util.UUID.randomUUID().toString()),
                    rolInicial
            );
            usuario.setEstado("ACTIVO");
            usuario.setTurno("FULL_TIME");
            log.info("[USER-SERVICE] Provisioning JIT (Entra ID) - nuevo usuario creado ID={} Rol={}", usuario.getEmail(), rolInicial);
        } else {
            // Usuario existente: SOLO actualizar rol si proviene explícitamente de un app role validado de Entra ID
            if (rolFromAppRole && rolSolicitado != Rol.CLIENTE) {
                usuario.setRol(rolSolicitado);
            }
            if (nombre != null && !nombre.isBlank()) {
                usuario.setNombre(nombre);
            }
            log.info("[USER-SERVICE] Sincronización JIT (Entra ID) - usuario ID={} Rol={}", usuario.getId(), usuario.getRol());
        }

        return UsuarioResponseDTO.fromEntity(usuarioRepository.save(usuario));
    }

    public UsuarioResponseDTO sincronizarUsuarioExterno(String email, String nombre, String rolNombre) {
        return sincronizarUsuarioExterno(email, nombre, rolNombre, false);
    }

    private Rol parseRol(String nombre) {
        if (nombre == null) return Rol.CLIENTE;
        try {
            return Rol.valueOf(nombre.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Rol.CLIENTE;
        }
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponseDTO> obtenerTodos() {
        return usuarioRepository.findAll().stream()
                .map(UsuarioResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UsuarioResponseDTO obtenerPorId(Long id) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con ID: " + id));
        return UsuarioResponseDTO.fromEntity(usuario);
    }

    @Transactional(readOnly = true)
    public UsuarioResponseDTO obtenerPorEmail(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con email: " + email));
        return UsuarioResponseDTO.fromEntity(usuario);
    }

    @Transactional
    public UsuarioResponseDTO actualizarUsuario(Long id, UsuarioCreateDTO dto) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con ID: " + id));

        if (dto.getEmail() != null && !dto.getEmail().isBlank()) {
            String newEmail = dto.getEmail().trim().toLowerCase();
            if (!usuario.getEmail().equalsIgnoreCase(newEmail) &&
                    usuarioRepository.findByEmail(newEmail).isPresent()) {
                throw new IllegalArgumentException("El email ya se encuentra registrado");
            }
            usuario.setEmail(newEmail);
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
            if (!"ACTIVO".equalsIgnoreCase(dto.getEstado()) && sessionService != null) {
                sessionService.revocarTodasLasSesiones(id);
            }
        }
        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            if (dto.getPassword().length() < 8) {
                throw new IllegalArgumentException("La nueva contraseña debe tener al menos 8 caracteres");
            }
            usuario.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
            if (sessionService != null) {
                sessionService.revocarTodasLasSesiones(id);
            }
        }

        Usuario actualizado = usuarioRepository.save(usuario);
        return UsuarioResponseDTO.fromEntity(actualizado);
    }

    @Transactional
    public void cambiarPassword(Long id, String currentPassword, String newPassword) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con ID: " + id));
        if (!passwordEncoder.matches(currentPassword, usuario.getPasswordHash())) {
            throw new IllegalArgumentException("La contraseña actual es incorrecta");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("La nueva contraseña debe tener al menos 8 caracteres");
        }
        usuario.setPasswordHash(passwordEncoder.encode(newPassword));
        usuarioRepository.save(usuario);
        if (sessionService != null) {
            sessionService.revocarTodasLasSesiones(id);
        }
        log.info("[USER-SERVICE] Contraseña cambiada para ID={}", id);
    }

    @Transactional
    public void cambiarPasswordPorEmail(String email, String currentPassword, String newPassword) {
        Usuario usuario = usuarioRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        cambiarPassword(usuario.getId(), currentPassword, newPassword);
    }

    @Transactional
    public UsuarioResponseDTO actualizarEstado(Long id, String nuevoEstado) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con ID: " + id));
        usuario.setEstado(nuevoEstado);
        if (!"ACTIVO".equalsIgnoreCase(nuevoEstado) && sessionService != null) {
            sessionService.revocarTodasLasSesiones(id);
        }
        Usuario guardado = usuarioRepository.save(usuario);
        return UsuarioResponseDTO.fromEntity(guardado);
    }

    @Transactional
    public void solicitarRecuperacionPassword(String email) {
        if (email == null || email.isBlank()) return;
        Optional<Usuario> usuarioOpt = usuarioRepository.findByEmail(email.trim().toLowerCase());
        if (usuarioOpt.isEmpty()) return;

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
            } catch (Exception ex) {
                log.warn("[USER-SERVICE] Fallo al publicar evento de recuperación: {}", ex.getMessage());
            }
        }
    }

    @Transactional
    public void eliminarUsuario(Long id) {
        if (!usuarioRepository.existsById(id)) {
            throw new IllegalArgumentException("Usuario no encontrado con ID: " + id);
        }
        if (sessionService != null) {
            sessionService.revocarTodasLasSesiones(id);
        }
        usuarioRepository.deleteById(id);
    }
}
