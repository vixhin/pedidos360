-- V3__create_rabbit_admin_tables.sql
-- Creación de tablas de administración dinámica y persistencia de eventos RabbitMQ

CREATE TABLE IF NOT EXISTS rabbit_exchange (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    type VARCHAR(50) NOT NULL,
    durable BOOLEAN NOT NULL DEFAULT TRUE,
    auto_delete BOOLEAN NOT NULL DEFAULT FALSE,
    internal BOOLEAN NOT NULL DEFAULT FALSE,
    protected_resource BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_rabbit_exchange_type CHECK (type IN ('DIRECT', 'TOPIC', 'FANOUT', 'HEADERS'))
);

CREATE TABLE IF NOT EXISTS rabbit_queue (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    durable BOOLEAN NOT NULL DEFAULT TRUE,
    exclusive BOOLEAN NOT NULL DEFAULT FALSE,
    auto_delete BOOLEAN NOT NULL DEFAULT FALSE,
    dead_letter_exchange_id BIGINT REFERENCES rabbit_exchange(id) ON DELETE SET NULL,
    dead_letter_routing_key VARCHAR(255),
    protected_resource BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS rabbit_binding (
    id BIGSERIAL PRIMARY KEY,
    queue_id BIGINT NOT NULL REFERENCES rabbit_queue(id) ON DELETE CASCADE,
    exchange_id BIGINT NOT NULL REFERENCES rabbit_exchange(id) ON DELETE CASCADE,
    routing_key VARCHAR(255) NOT NULL,
    protected_resource BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_rabbit_binding UNIQUE (queue_id, exchange_id, routing_key)
);

CREATE TABLE IF NOT EXISTS rabbit_processed_event (
    event_id VARCHAR(100) NOT NULL,
    consumer VARCHAR(100) NOT NULL,
    event_type VARCHAR(100),
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id, consumer)
);

CREATE INDEX IF NOT EXISTS idx_rabbit_processed_event_consumer ON rabbit_processed_event(consumer);
