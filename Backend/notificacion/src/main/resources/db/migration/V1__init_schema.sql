CREATE TABLE IF NOT EXISTS notificaciones (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    mensaje TEXT NOT NULL,
    canal VARCHAR(50) NOT NULL,
    fecha_envio TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Datos iniciales de prueba
INSERT INTO notificaciones (usuario_id, mensaje, canal) VALUES 
(1, 'Bienvenido a Pedidos360', 'EMAIL'),
(1, 'Tu pedido #1 ha sido completado', 'PUSH');
