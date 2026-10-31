-- V3__create_processed_events_table.sql
-- Tabla unificada para idempotencia persistente de eventos consumidos (Kafka y RabbitMQ)

CREATE TABLE IF NOT EXISTS processed_event (
    event_id VARCHAR(100) NOT NULL PRIMARY KEY,
    source_channel VARCHAR(50) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    resource_id VARCHAR(100),
    correlation_id VARCHAR(100),
    consumer_group VARCHAR(100),
    processed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_processed_event_resource ON processed_event(resource_id, event_type);

