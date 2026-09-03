CREATE TABLE IF NOT EXISTS analitica_eventos (
    id BIGSERIAL PRIMARY KEY,
    tipo_evento VARCHAR(100) NOT NULL,
    descripcion TEXT NOT NULL,
    fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Datos iniciales de prueba
INSERT INTO analitica_eventos (tipo_evento, descripcion) VALUES 
('LOGIN', 'Inicio de sesión de usuario ID 1'),
('COMPRA', 'Pedido ID 1 realizado con éxito');
