-- Migración Flyway V8: Idempotencia en pedidos y límites de uso en cupones
ALTER TABLE cupones ADD COLUMN IF NOT EXISTS usos_maximos INT DEFAULT 1000;
ALTER TABLE cupones ADD COLUMN IF NOT EXISTS usos_actuales INT DEFAULT 0;
ALTER TABLE cupones ADD COLUMN IF NOT EXISTS descuento_maximo NUMERIC(10, 2);

ALTER TABLE pedidos ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128);
CREATE UNIQUE INDEX IF NOT EXISTS idx_pedidos_idempotency_key ON pedidos(idempotency_key) WHERE idempotency_key IS NOT NULL;

