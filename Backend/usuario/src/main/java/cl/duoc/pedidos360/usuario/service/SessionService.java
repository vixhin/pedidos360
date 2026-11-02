package cl.duoc.pedidos360.usuario.service;

import cl.duoc.pedidos360.usuario.entity.UserSession;
import cl.duoc.pedidos360.usuario.repository.UserSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserSessionRepository sessionRepository;
    private final Duration refreshTokenTtl;

    public SessionService(
            UserSessionRepository sessionRepository,
            @Value("${auth.refresh-token-ttl:PT8H}") String refreshTokenTtlStr) {
        this.sessionRepository = sessionRepository;
        this.refreshTokenTtl = Duration.parse(refreshTokenTtlStr);
    }

    public static class SessionCreationResult {
        private final String sid;
        private final String rawRefreshToken;
        private final LocalDateTime expiresAt;

        public SessionCreationResult(String sid, String rawRefreshToken, LocalDateTime expiresAt) {
            this.sid = sid;
            this.rawRefreshToken = rawRefreshToken;
            this.expiresAt = expiresAt;
        }

        public String getSid() { return sid; }
        public String getRawRefreshToken() { return rawRefreshToken; }
        public LocalDateTime getExpiresAt() { return expiresAt; }
    }

    @Transactional
    public SessionCreationResult crearSesion(Long usuarioId, String userAgent, String ipAddress) {
        String sid = UUID.randomUUID().toString();
        String rawRefreshToken = generarTokenAleatorio(32);
        String hash = sha256(rawRefreshToken);
        LocalDateTime expiresAt = LocalDateTime.now().plus(refreshTokenTtl);

        UserSession session = new UserSession(sid, usuarioId, hash, expiresAt, userAgent, ipAddress);
        sessionRepository.save(session);
        log.info("[SESSION] Nueva sesión creada sid={} para usuarioId={}", sid, usuarioId);

        return new SessionCreationResult(sid, rawRefreshToken, expiresAt);
    }

    @Transactional
    public Optional<SessionCreationResult> rotarRefreshToken(String rawRefreshToken, String userAgent, String ipAddress) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return Optional.empty();
        }

        String presentedHash = sha256(rawRefreshToken.trim());
        // Buscar todas las sesiones o buscar por hash
        // Como no tenemos hash directo como índice primario, podemos iterar o buscar la sesión activa
        // Para mayor seguridad y evitar escaneo completo, buscamos por hash
        Optional<UserSession> sessionOpt = sessionRepository.findAll().stream()
                .filter(s -> MessageDigest.isEqual(s.getRefreshTokenHash().getBytes(StandardCharsets.UTF_8),
                        presentedHash.getBytes(StandardCharsets.UTF_8)))
                .findFirst();

        if (sessionOpt.isEmpty()) {
            log.warn("[SESSION] Intento de refresh con token no reconocido");
            return Optional.empty();
        }

        UserSession session = sessionOpt.get();

        if (session.isRevoked()) {
            log.warn("[SESSION][REUSE-DETECTED] ¡Intento de reutilización de refresh token en sesión revocada! sid={} usuarioId={}",
                    session.getSid(), session.getUsuarioId());
            // Si detectamos reutilización maliciosa, revocar todas las sesiones del usuario como medida defensiva
            sessionRepository.revokeAllByUsuarioId(session.getUsuarioId(), LocalDateTime.now());
            return Optional.empty();
        }

        if (session.isExpired()) {
            log.info("[SESSION] Sesión expirada sid={}", session.getSid());
            session.setRevokedAt(LocalDateTime.now());
            sessionRepository.save(session);
            return Optional.empty();
        }

        // Rotación legítima de token: emitir nuevo refresh token
        String nuevoRefreshToken = generarTokenAleatorio(32);
        String nuevoHash = sha256(nuevoRefreshToken);
        LocalDateTime nuevoExpiresAt = LocalDateTime.now().plus(refreshTokenTtl);

        session.setRefreshTokenHash(nuevoHash);
        session.setExpiresAt(nuevoExpiresAt);
        session.setLastUsedAt(LocalDateTime.now());
        if (userAgent != null) session.setUserAgent(userAgent);
        if (ipAddress != null) session.setIpAddress(ipAddress);

        sessionRepository.save(session);
        log.info("[SESSION] Refresh exitoso para sid={} usuarioId={}", session.getSid(), session.getUsuarioId());

        return Optional.of(new SessionCreationResult(session.getSid(), nuevoRefreshToken, nuevoExpiresAt));
    }

    @Transactional(readOnly = true)
    public boolean esSesionValida(String sid) {
        if (sid == null || sid.isBlank()) return false;
        return sessionRepository.findBySid(sid)
                .map(UserSession::isValid)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public Optional<UserSession> obtenerSesion(String sid) {
        if (sid == null || sid.isBlank()) return Optional.empty();
        return sessionRepository.findBySid(sid);
    }

    @Transactional
    public void revocarSesion(String sid) {
        if (sid == null || sid.isBlank()) return;
        sessionRepository.findBySid(sid).ifPresent(session -> {
            session.setRevokedAt(LocalDateTime.now());
            sessionRepository.save(session);
            log.info("[SESSION] Sesión revocada sid={}", sid);
        });
    }

    @Transactional
    public void revocarTodasLasSesiones(Long usuarioId) {
        if (usuarioId == null) return;
        int count = sessionRepository.revokeAllByUsuarioId(usuarioId, LocalDateTime.now());
        log.info("[SESSION] Se revocaron {} sesiones para usuarioId={}", count, usuarioId);
    }

    @Scheduled(cron = "${auth.session-cleanup-cron:0 0 3 * * ?}")
    @Transactional
    public void limpiarSesionesExpiradas() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        int eliminadas = sessionRepository.deleteExpiredBefore(cutoff);
        if (eliminadas > 0) {
            log.info("[SESSION] Limpieza automática eliminó {} sesiones inactivas/expiradas", eliminadas);
        }
    }

    public static String generarTokenAleatorio(int numBytes) {
        byte[] bytes = new byte[numBytes];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 no disponible", e);
        }
    }
}

