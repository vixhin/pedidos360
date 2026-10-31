-- V4__enhance_outbox_table.sql
-- Optimizaciones y campos de recuperación para Transactional Outbox seguro y concurrente

ALTER TABLE outbox_event
    ADD COLUMN IF NOT EXISTS next_retry_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS processing_started_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS locked_by VARCHAR(100),
    ADD COLUMN IF NOT EXISTS last_attempted_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS error_type VARCHAR(50);

CREATE INDEX IF NOT EXISTS idx_outbox_fetch ON outbox_event(status, next_retry_at, created_at);
CREATE INDEX IF NOT EXISTS idx_outbox_stuck ON outbox_event(status, processing_started_at);

