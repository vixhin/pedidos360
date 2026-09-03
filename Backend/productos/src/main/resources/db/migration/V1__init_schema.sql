CREATE TABLE IF NOT EXISTS productos (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(255) NOT NULL,
    descripcion TEXT,
    precio NUMERIC(10, 2) NOT NULL,
    stock INTEGER NOT NULL
);

-- Datos iniciales de prueba
INSERT INTO productos (nombre, descripcion, precio, stock) VALUES 
('Producto A', 'Descripción del producto A', 4990.00, 100),
('Producto B', 'Descripción del producto B', 9990.00, 50);
