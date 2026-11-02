-- Migración Flyway V3: Tabla de reservas de stock atómicas e idempotentes
CREATE TABLE IF NOT EXISTS stock_reservas (
    id BIGSERIAL PRIMARY KEY,
    reserva_id VARCHAR(128) NOT NULL UNIQUE,
    pedido_id BIGINT,
    estado VARCHAR(50) NOT NULL DEFAULT 'RESERVADO', -- 'RESERVADO', 'CONFIRMADO', 'LIBERADO'
    items_json TEXT NOT NULL,
    creado_el TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expira_el TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_stock_reservas_reserva_id ON stock_reservas(reserva_id);

