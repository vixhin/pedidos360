-- Migración Flyway V3: Agregar soporte para estado de lectura en notificaciones
ALTER TABLE notificaciones ADD COLUMN IF NOT EXISTS leida BOOLEAN NOT NULL DEFAULT FALSE;

