-- Migración Flyway V6: Tabla de sesiones persistidas para autenticación segura HttpOnly y rotación de tokens
CREATE TABLE IF NOT EXISTS user_sessions (
    id BIGSERIAL PRIMARY KEY,
    sid VARCHAR(64) NOT NULL UNIQUE,
    usuario_id BIGINT NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    refresh_token_hash VARCHAR(128) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    user_agent VARCHAR(512),
    ip_address VARCHAR(128)
);

CREATE INDEX IF NOT EXISTS idx_user_sessions_sid ON user_sessions(sid);
CREATE INDEX IF NOT EXISTS idx_user_sessions_usuario ON user_sessions(usuario_id);
CREATE INDEX IF NOT EXISTS idx_user_sessions_expires ON user_sessions(expires_at);

