-- V5__add_reservation_id_to_outbox.sql
-- Identificador único de reserva para control estricto de propiedad (lease) y actualización condicional

ALTER TABLE outbox_event
    ADD COLUMN IF NOT EXISTS reservation_id VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_outbox_reservation ON outbox_event(id, reservation_id);

