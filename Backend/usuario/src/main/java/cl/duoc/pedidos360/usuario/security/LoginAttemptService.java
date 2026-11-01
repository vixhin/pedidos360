package cl.duoc.pedidos360.usuario.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_TIME_SECONDS = 15 * 60; // 15 minutos

    private static class AttemptInfo {
        int attempts;
        Instant lockExpiration;

        AttemptInfo(int attempts, Instant lockExpiration) {
            this.attempts = attempts;
            this.lockExpiration = lockExpiration;
        }
    }

    private final Map<String, AttemptInfo> attemptsCache = new ConcurrentHashMap<>();

    public boolean isBlocked(String key) {
        if (key == null) return false;
        String normalizedKey = key.trim().toLowerCase();
        AttemptInfo info = attemptsCache.get(normalizedKey);
        if (info == null) return false;

        if (info.lockExpiration != null) {
            if (Instant.now().isBefore(info.lockExpiration)) {
                log.warn("[LOGIN-ATTEMPTS] Account/IP {} is temporarily locked until {}", normalizedKey, info.lockExpiration);
                return true;
            } else {
                // Bloqueo expirado
                attemptsCache.remove(normalizedKey);
                return false;
            }
        }
        return false;
    }

    public void loginFailed(String key) {
        if (key == null) return;
        String normalizedKey = key.trim().toLowerCase();
        attemptsCache.compute(normalizedKey, (k, old) -> {
            if (old == null) {
                return new AttemptInfo(1, null);
            }
            int newAttempts = old.attempts + 1;
            Instant lockExp = old.lockExpiration;
            if (newAttempts >= MAX_ATTEMPTS) {
                lockExp = Instant.now().plusSeconds(LOCK_TIME_SECONDS);
                log.warn("[LOGIN-ATTEMPTS] Account/IP {} exceeded maximum failed attempts ({}), locking for 15 minutes", normalizedKey, MAX_ATTEMPTS);
            }
            return new AttemptInfo(newAttempts, lockExp);
        });
    }

    public void loginSucceeded(String key) {
        if (key == null) return;
        attemptsCache.remove(key.trim().toLowerCase());
    }
}

