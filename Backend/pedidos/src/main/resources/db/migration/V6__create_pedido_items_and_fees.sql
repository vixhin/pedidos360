-- Migración Flyway V6: Crear tabla pedido_items y tarifas validadas en backend
ALTER TABLE pedidos ADD COLUMN IF NOT EXISTS subtotal NUMERIC(10, 2);
ALTER TABLE pedidos ADD COLUMN IF NOT EXISTS tarifa_servicio NUMERIC(10, 2) DEFAULT 1500.00;
ALTER TABLE pedidos ADD COLUMN IF NOT EXISTS costo_envio NUMERIC(10, 2) DEFAULT 0.00;
ALTER TABLE pedidos ADD COLUMN IF NOT EXISTS direccion_entrega VARCHAR(255);
ALTER TABLE pedidos ADD COLUMN IF NOT EXISTS repartidor_nombre VARCHAR(100);
ALTER TABLE pedidos ADD COLUMN IF NOT EXISTS repartidor_telefono VARCHAR(50);

CREATE TABLE IF NOT EXISTS pedido_items (
    id BIGSERIAL PRIMARY KEY,
    pedido_id BIGINT NOT NULL REFERENCES pedidos(id) ON DELETE CASCADE,
    producto_id BIGINT NOT NULL,
    nombre VARCHAR(255) NOT NULL,
    icono VARCHAR(100),
    cantidad INTEGER NOT NULL,
    precio_unitario NUMERIC(10, 2) NOT NULL
);

