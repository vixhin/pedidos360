-- V4__enhance_processed_events_table.sql
-- Optimizaciones y campos adicionales para idempotencia persistente de eventos

ALTER TABLE processed_event
    ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'COMPLETED',
    ADD COLUMN IF NOT EXISTS error_message TEXT,
    ADD COLUMN IF NOT EXISTS execution_duration_ms BIGINT;

CREATE INDEX IF NOT EXISTS idx_processed_event_created ON processed_event(processed_at);
CREATE INDEX IF NOT EXISTS idx_processed_event_channel ON processed_event(source_channel, event_type);

