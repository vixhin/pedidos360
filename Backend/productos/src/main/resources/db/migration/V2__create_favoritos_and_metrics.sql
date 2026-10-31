-- Migración Flyway V2: Soporte para favoritos de usuario, métricas de catálogo y promociones reales
ALTER TABLE productos ADD COLUMN IF NOT EXISTS rating NUMERIC(2, 1) DEFAULT 4.8;
ALTER TABLE productos ADD COLUMN IF NOT EXISTS delivery_minutes INTEGER DEFAULT 25;

CREATE TABLE IF NOT EXISTS favoritos (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    producto_id BIGINT NOT NULL REFERENCES productos(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_usuario_producto UNIQUE (usuario_id, producto_id)
);

CREATE TABLE IF NOT EXISTS promociones (
    id VARCHAR(50) PRIMARY KEY,
    titulo VARCHAR(255) NOT NULL,
    tag VARCHAR(50) NOT NULL,
    color VARCHAR(50) NOT NULL,
    activa BOOLEAN DEFAULT TRUE
);

INSERT INTO promociones (id, titulo, tag, color, activa) VALUES
('p1', 'Hasta 40% de descuento en productos de supermercado', 'Termina hoy', '#fde7c8', true),
('p2', 'Envío gratis en tu primera compra de abarrotes', 'Nuevo', '#d7f7e9', true),
('p3', 'Ofertas especiales en carnes y lácteos', 'Destacado', '#e4e6ff', true),
('p4', 'Compra 2 y obtén un precio especial en bebidas', 'Termina hoy', '#ffe3e6', true)
ON CONFLICT (id) DO NOTHING;

