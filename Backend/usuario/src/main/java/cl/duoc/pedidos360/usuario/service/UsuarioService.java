package cl.duoc.pedidos360.usuario.service;

import cl.duoc.pedidos360.usuario.dto.AuthRequest;
import cl.duoc.pedidos360.usuario.dto.AuthResponse;
import cl.duoc.pedidos360.usuario.dto.RegisterRequest;
import cl.duoc.pedidos360.usuario.entity.Usuario;
import cl.duoc.pedidos360.usuario.enums.Rol;
import cl.duoc.pedidos360.usuario.repository.UsuarioRepository;
import cl.duoc.pedidos360.usuario.security.JwtUtil;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UsuarioService {

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
    public void initDefaultUsers() {
        // Mock Admin requested by user (password: chupalovixo)
        if (usuarioRepository.findByEmail("admin@pedidos360.cl").isEmpty()) {
            Usuario admin = new Usuario(
                    null,
                    "Administrador Vixo",
                    "admin@pedidos360.cl",
                    passwordEncoder.encode("chupalovixo"),
                    Rol.ADMIN
            );
            usuarioRepository.save(admin);
        }

        // Mock Vendedor
        if (usuarioRepository.findByEmail("vendedor@pedidos360.cl").isEmpty()) {
            Usuario vendedor = new Usuario(
                    null,
                    "Vendedor Ventas",
                    "vendedor@pedidos360.cl",
                    passwordEncoder.encode("vendedor123"),
                    Rol.VENDEDOR
            );
            usuarioRepository.save(vendedor);
        }

        // Mock Cliente
        if (usuarioRepository.findByEmail("cliente@pedidos360.cl").isEmpty()) {
            Usuario cliente = new Usuario(
                    null,
                    "Cliente Compras",
                    "cliente@pedidos360.cl",
                    passwordEncoder.encode("cliente123"),
                    Rol.CLIENTE
            );
            usuarioRepository.save(cliente);
        }
    }

    public AuthResponse autenticar(AuthRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Credenciales inválidas: Usuario no encontrado"));

        if (!passwordEncoder.matches(request.getPassword(), usuario.getPassword())) {
            throw new RuntimeException("Credenciales inválidas: Contraseña incorrecta");
        }

        String token = jwtUtil.generateToken(usuario.getEmail(), usuario.getRol());
        return new AuthResponse(token, usuario.getId(), usuario.getNombre(), usuario.getEmail(), usuario.getRol());
    }

    public AuthResponse registrar(RegisterRequest request) {
        if (usuarioRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("El email ya se encuentra registrado");
        }

        Rol rolAsignado = (request.getRol() != null) ? request.getRol() : Rol.CLIENTE;

        Usuario usuario = new Usuario(
                null,
                request.getNombre(),
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                rolAsignado
        );

        Usuario guardado = usuarioRepository.save(usuario);
        String token = jwtUtil.generateToken(guardado.getEmail(), guardado.getRol());
        return new AuthResponse(token, guardado.getId(), guardado.getNombre(), guardado.getEmail(), guardado.getRol());
    }

    public List<Usuario> obtenerTodos() {
        return usuarioRepository.findAll();
    }

    public Optional<Usuario> obtenerPorId(Long id) {
        return usuarioRepository.findById(id);
    }

    public Usuario guardar(Usuario usuario) {
        if (usuario.getPassword() != null && !usuario.getPassword().startsWith("$2a$")) {
            usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
        }
        return usuarioRepository.save(usuario);
    }

    public void eliminar(Long id) {
        usuarioRepository.deleteById(id);
    }
}
