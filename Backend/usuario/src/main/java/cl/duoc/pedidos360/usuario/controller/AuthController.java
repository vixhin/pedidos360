package cl.duoc.pedidos360.usuario.controller;

import cl.duoc.pedidos360.usuario.dto.*;
import cl.duoc.pedidos360.usuario.entity.Usuario;
import cl.duoc.pedidos360.usuario.security.JwtUtil;
import cl.duoc.pedidos360.usuario.service.SessionService;
import cl.duoc.pedidos360.usuario.service.UsuarioService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UsuarioService usuarioService;
    private final SessionService sessionService;
    private final JwtUtil jwtUtil;

    @Value("${auth.cookie.secure:false}")
    private boolean cookieSecure;

    @Value("${auth.cookie.domain:}")
    private String cookieDomain;

    @Value("${auth.refresh-token-ttl:PT8H}")
    private String refreshTokenTtlStr;

    @Autowired(required = false)
    private HttpServletRequest request;

    public AuthController(UsuarioService usuarioService,
                          SessionService sessionService,
                          JwtUtil jwtUtil) {
        this.usuarioService = usuarioService;
        this.sessionService = sessionService;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/bootstrap-admin")
    public ResponseEntity<ApiResponse<UsuarioResponseDTO>> bootstrapAdmin(@Valid @RequestBody AdminBootstrapDTO req) {
        UsuarioResponseDTO admin = usuarioService.bootstrapAdmin(req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Administrador inicial creado exitosamente", admin));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody AuthRequest authRequest) {
        String clientIp = getClientIp(request);
        String userAgent = request != null ? request.getHeader("User-Agent") : "unknown";

        Usuario usuario = usuarioService.autenticarUsuario(
                authRequest.getEmail(),
                authRequest.getPassword(),
                authRequest.getTurnstileToken(),
                clientIp
        );

        String sid = java.util.UUID.randomUUID().toString();
        String rawRefreshToken = null;

        if (sessionService != null) {
            SessionService.SessionCreationResult sessionResult =
                    sessionService.crearSesion(usuario.getId(), userAgent, clientIp);
            sid = sessionResult.getSid();
            rawRefreshToken = sessionResult.getRawRefreshToken();
        }

        long accessDurationMs = (jwtUtil != null) ? jwtUtil.getExpirationMs() : 900_000L;
        String jwtToken = (jwtUtil != null)
                ? jwtUtil.generateToken(usuario.getId(), usuario.getEmail(), usuario.getRol(), sid)
                : "dev-token";

        long expiresAtTimestamp = System.currentTimeMillis() + accessDurationMs;
        String csrfToken = SessionService.generarTokenAleatorio(24);

        HttpHeaders headers = new HttpHeaders();
        // 1. Cookie de sesión HttpOnly con JWT de acceso
        ResponseCookie sessionCookie = buildCookie("p360_session", jwtToken, "/",
                Duration.ofMillis(accessDurationMs), true, "Lax");
        headers.add(HttpHeaders.SET_COOKIE, sessionCookie.toString());

        // 2. Cookie de rotación HttpOnly con Refresh Token
        if (rawRefreshToken != null) {
            Duration refreshDuration = Duration.parse(refreshTokenTtlStr);
            ResponseCookie refreshCookie = buildCookie("p360_refresh", rawRefreshToken, "/api/auth",
                    refreshDuration, true, "Strict");
            headers.add(HttpHeaders.SET_COOKIE, refreshCookie.toString());

            // 3. Cookie XSRF-TOKEN (legible por Angular)
            ResponseCookie xsrfCookie = buildCookie("XSRF-TOKEN", csrfToken, "/",
                    refreshDuration, false, "Lax");
            headers.add(HttpHeaders.SET_COOKIE, xsrfCookie.toString());
        }

        // El cuerpo JSON NO expone el token JWT en localStorage/sessionStorage
        AuthResponse responseData = new AuthResponse(
                null, // No exponer JWT en el body
                usuario.getId(),
                usuario.getNombre(),
                usuario.getEmail(),
                usuario.getRol(),
                expiresAtTimestamp
        );

        return ResponseEntity.ok()
                .headers(headers)
                .body(ApiResponse.ok("Autenticación exitosa", responseData));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh() {
        if (sessionService == null || jwtUtil == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Servicio de sesiones no disponible"));
        }

        String rawRefreshToken = getCookieValue(request, "p360_refresh");
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Refresh token ausente o expirado"));
        }

        String clientIp = getClientIp(request);
        String userAgent = request != null ? request.getHeader("User-Agent") : "unknown";

        Optional<SessionService.SessionCreationResult> rotationResult =
                sessionService.rotarRefreshToken(rawRefreshToken, userAgent, clientIp);

        if (rotationResult.isEmpty()) {
            HttpHeaders cleanHeaders = new HttpHeaders();
            cleanHeaders.add(HttpHeaders.SET_COOKIE, cleanCookie("p360_session", "/").toString());
            cleanHeaders.add(HttpHeaders.SET_COOKIE, cleanCookie("p360_refresh", "/api/auth").toString());
            cleanHeaders.add(HttpHeaders.SET_COOKIE, cleanCookie("XSRF-TOKEN", "/").toString());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .headers(cleanHeaders)
                    .body(ApiResponse.error("Sesión inválida, revocada o reutilizada indebidamente"));
        }

        SessionService.SessionCreationResult nuevoResult = rotationResult.get();
        Optional<cl.duoc.pedidos360.usuario.entity.UserSession> sessOpt =
                sessionService.obtenerSesion(nuevoResult.getSid());

        if (sessOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Sesión no encontrada"));
        }

        UsuarioResponseDTO userDto = usuarioService.obtenerPorId(sessOpt.get().getUsuarioId());
        String nuevoJwt = jwtUtil.generateToken(userDto.getId(), userDto.getEmail(), userDto.getRol(), nuevoResult.getSid());
        long accessDurationMs = jwtUtil.getExpirationMs();
        long expiresAtTimestamp = System.currentTimeMillis() + accessDurationMs;
        String nuevoCsrf = SessionService.generarTokenAleatorio(24);
        Duration refreshDuration = Duration.parse(refreshTokenTtlStr);

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, buildCookie("p360_session", nuevoJwt, "/", Duration.ofMillis(accessDurationMs), true, "Lax").toString());
        headers.add(HttpHeaders.SET_COOKIE, buildCookie("p360_refresh", nuevoResult.getRawRefreshToken(), "/api/auth", refreshDuration, true, "Strict").toString());
        headers.add(HttpHeaders.SET_COOKIE, buildCookie("XSRF-TOKEN", nuevoCsrf, "/", refreshDuration, false, "Lax").toString());

        AuthResponse responseData = new AuthResponse(
                null,
                userDto.getId(),
                userDto.getNombre(),
                userDto.getEmail(),
                userDto.getRol(),
                expiresAtTimestamp
        );

        return ResponseEntity.ok()
                .headers(headers)
                .body(ApiResponse.ok("Sesión renovada exitosamente", responseData));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        if (request != null && sessionService != null) {
            String sid = (String) request.getAttribute("p360.sid");
            if (sid != null) {
                sessionService.revocarSesion(sid);
            }
        }

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, cleanCookie("p360_session", "/").toString());
        headers.add(HttpHeaders.SET_COOKIE, cleanCookie("p360_refresh", "/api/auth").toString());
        headers.add(HttpHeaders.SET_COOKIE, cleanCookie("XSRF-TOKEN", "/").toString());

        return ResponseEntity.ok()
                .headers(headers)
                .body(ApiResponse.ok("Sesión cerrada correctamente", null));
    }

    @GetMapping("/session")
    public ResponseEntity<ApiResponse<AuthResponse>> getSession() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equalsIgnoreCase(auth.getName())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("No hay una sesión activa"));
        }

        String email = auth.getName();
        try {
            UsuarioResponseDTO user = usuarioService.obtenerPorEmail(email);
            long accessDurationMs = (jwtUtil != null) ? jwtUtil.getExpirationMs() : 900_000L;
            AuthResponse responseData = new AuthResponse(
                    null,
                    user.getId(),
                    user.getNombre(),
                    user.getEmail(),
                    user.getRol(),
                    System.currentTimeMillis() + accessDurationMs
            );
            return ResponseEntity.ok(ApiResponse.ok("Sesión activa", responseData));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error("Usuario no encontrado"));
        }
    }

    @GetMapping("/session/verify")
    public ResponseEntity<Void> verifySessionForwardAuth() {
        String token = getCookieValue(request, "p360_session");
        if (token == null || token.isBlank()) {
            // Usuario anónimo: Caddy forward_auth devuelve 200 para que el servicio destino decida
            return ResponseEntity.ok().build();
        }

        if (jwtUtil != null && jwtUtil.validateToken(token)) {
            String sid = jwtUtil.extractSessionId(token);
            if (sid == null || sessionService == null || sessionService.esSesionValida(sid)) {
                return ResponseEntity.ok().build();
            }
        }

        // Credencial inválida o revocada
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, cleanCookie("p360_session", "/").toString());
        headers.add(HttpHeaders.SET_COOKIE, cleanCookie("p360_refresh", "/api/auth").toString());
        headers.add(HttpHeaders.SET_COOKIE, cleanCookie("XSRF-TOKEN", "/").toString());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).headers(headers).build();
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UsuarioResponseDTO>> register(@Valid @RequestBody UsuarioCreateDTO req) {
        String clientIp = getClientIp(request);
        UsuarioResponseDTO response = usuarioService.registrarCliente(req, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Usuario registrado correctamente", response));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@RequestParam String email) {
        usuarioService.solicitarRecuperacionPassword(email);
        return ResponseEntity.ok(ApiResponse.ok("Si el correo existe, se enviaron instrucciones para restablecer la contraseña.", null));
    }

    private ResponseCookie buildCookie(String name, String value, String path, Duration maxAge, boolean httpOnly, String sameSite) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
                .httpOnly(httpOnly)
                .secure(cookieSecure)
                .sameSite(sameSite)
                .path(path)
                .maxAge(maxAge);

        if (cookieDomain != null && !cookieDomain.isBlank()) {
            builder.domain(cookieDomain.trim());
        }
        return builder.build();
    }

    private ResponseCookie cleanCookie(String name, String path) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path(path)
                .maxAge(0);

        if (cookieDomain != null && !cookieDomain.isBlank()) {
            builder.domain(cookieDomain.trim());
        }
        return builder.build();
    }

    private String getCookieValue(HttpServletRequest req, String name) {
        if (req == null) return null;
        Cookie[] cookies = req.getCookies();
        if (cookies != null) {
            for (Cookie c : cookies) {
                if (name.equals(c.getName())) {
                    return c.getValue();
                }
            }
        }
        return null;
    }

    private String getClientIp(HttpServletRequest req) {
        if (req == null) return null;
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
