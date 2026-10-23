-- V2__create_rabbit_processed_event.sql
-- Tabla para idempotencia persistente de eventos consumidos por analitica-service

CREATE TABLE IF NOT EXISTS rabbit_processed_event (
    event_id VARCHAR(100) NOT NULL,
    consumer VARCHAR(100) NOT NULL,
    event_type VARCHAR(100),
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id, consumer)
);

CREATE INDEX IF NOT EXISTS idx_rabbit_processed_event_consumer ON rabbit_processed_event(consumer);
