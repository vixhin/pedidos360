-- ==============================================================================
-- Migración Flyway V7: Reglas Comerciales Dinámicas, Cupones y Cobertura
-- ==============================================================================

-- 1. Tabla de configuración comercial autoritativa del backend
CREATE TABLE IF NOT EXISTS configuracion_comercial (
    id BIGSERIAL PRIMARY KEY,
    clave VARCHAR(50) UNIQUE NOT NULL,
    valor NUMERIC(10, 2) NOT NULL,
    descripcion VARCHAR(255),
    actualizado_el TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO configuracion_comercial (clave, valor, descripcion)
VALUES
    ('UMBRAL_ENVIO_GRATIS', 20000.00, 'Subtotal mínimo requerido para envío gratis'),
    ('COSTO_ENVIO_BASE', 2990.00, 'Tarifa base de envío a domicilio'),
    ('TARIFA_SERVICIO', 1500.00, 'Tarifa fija por servicio de plataforma')
ON CONFLICT (clave) DO NOTHING;

-- 2. Tabla de cupones de descuento administrados por el servidor
CREATE TABLE IF NOT EXISTS cupones (
    id BIGSERIAL PRIMARY KEY,
    codigo VARCHAR(50) UNIQUE NOT NULL,
    tipo_descuento VARCHAR(20) NOT NULL DEFAULT 'PORCENTAJE', -- 'PORCENTAJE' o 'MONTO_FIJO'
    valor NUMERIC(10, 2) NOT NULL,
    monto_minimo NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    vigente_hasta TIMESTAMP
);

INSERT INTO cupones (codigo, tipo_descuento, valor, monto_minimo, activo)
VALUES
    ('BIENVENIDA360', 'PORCENTAJE', 10.00, 5000.00, TRUE)
ON CONFLICT (codigo) DO NOTHING;

-- 3. Tabla de comunas con cobertura geográfica real y tiempos de entrega
CREATE TABLE IF NOT EXISTS cobertura_comunas (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(100) UNIQUE NOT NULL,
    region VARCHAR(100) NOT NULL DEFAULT 'Región Metropolitana',
    tiempo_estimado_minutos INTEGER NOT NULL DEFAULT 35,
    costo_adicional NUMERIC(10, 2) NOT NULL DEFAULT 0.00,
    activa BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO cobertura_comunas (nombre, region, tiempo_estimado_minutos, costo_adicional, activa)
VALUES
    ('Santiago', 'Región Metropolitana', 30, 0.00, TRUE),
    ('Providencia', 'Región Metropolitana', 25, 0.00, TRUE),
    ('Las Condes', 'Región Metropolitana', 35, 0.00, TRUE),
    ('Ñuñoa', 'Región Metropolitana', 30, 0.00, TRUE),
    ('San Miguel', 'Región Metropolitana', 35, 0.00, TRUE),
    ('La Florida', 'Región Metropolitana', 40, 0.00, TRUE)
ON CONFLICT (nombre) DO NOTHING;

