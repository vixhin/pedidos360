CREATE TABLE IF NOT EXISTS carrito_items (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    producto_id BIGINT NOT NULL,
    cantidad INTEGER NOT NULL,
    precio_unitario NUMERIC(10, 2) NOT NULL
);

-- Datos iniciales de prueba
INSERT INTO carrito_items (usuario_id, producto_id, cantidad, precio_unitario) VALUES 
(1, 101, 2, 4990.00),
(1, 102, 1, 9990.00);
