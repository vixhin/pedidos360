-- Migración Flyway V1: Esquema Inicial para el ChatService

CREATE TABLE IF NOT EXISTS chat_conversacion (
    id BIGSERIAL PRIMARY KEY,
    pedido_id BIGINT NOT NULL,
    cliente_id BIGINT NOT NULL,
    repartidor_id BIGINT NOT NULL,
    estado VARCHAR(50) NOT NULL DEFAULT 'ABIERTO',
    fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    fecha_cierre TIMESTAMP NULL,
    deleted_for_client_at TIMESTAMP NULL,
    deleted_for_courier_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chat_conv_pedido ON chat_conversacion(pedido_id);
CREATE INDEX IF NOT EXISTS idx_chat_conv_cliente ON chat_conversacion(cliente_id);
CREATE INDEX IF NOT EXISTS idx_chat_conv_repartidor ON chat_conversacion(repartidor_id);

CREATE TABLE IF NOT EXISTS chat_mensaje (
    id BIGSERIAL PRIMARY KEY,
    conversacion_id BIGINT NOT NULL REFERENCES chat_conversacion(id) ON DELETE CASCADE,
    remitente_id BIGINT NOT NULL,
    tipo_remitente VARCHAR(50) NOT NULL,
    contenido TEXT NOT NULL,
    estado VARCHAR(50) NOT NULL DEFAULT 'ENVIADO',
    fecha_envio TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    fecha_entrega TIMESTAMP NULL,
    fecha_lectura TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chat_msg_conversacion ON chat_mensaje(conversacion_id);
