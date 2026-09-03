CREATE TABLE IF NOT EXISTS pedidos (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    total NUMERIC(10, 2) NOT NULL,
    estado VARCHAR(50) NOT NULL,
    fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Datos iniciales de prueba
INSERT INTO pedidos (usuario_id, total, estado) VALUES 
(1, 29990.00, 'COMPLETADO'),
(1, 15990.00, 'PENDIENTE');
