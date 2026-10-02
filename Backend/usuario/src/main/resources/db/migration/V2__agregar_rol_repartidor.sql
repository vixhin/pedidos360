-- Migración Flyway V2: Normalizar y agregar rol REPARTIDOR

-- Actualizar al usuario de prueba Rodrigo Morales al nuevo rol REPARTIDOR
UPDATE usuarios 
SET rol = 'REPARTIDOR' 
WHERE email = 'rodrigo.morales@pedidos360.cl';
