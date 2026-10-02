-- Migración Flyway V2: Asegurar columna repartidor_id e índice en tabla pedidos

ALTER TABLE pedidos ADD COLUMN IF NOT EXISTS repartidor_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_pedidos_repartidor_id ON pedidos(repartidor_id);
