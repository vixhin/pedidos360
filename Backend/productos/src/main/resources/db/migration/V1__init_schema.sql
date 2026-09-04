CREATE TABLE IF NOT EXISTS productos (
    id BIGSERIAL PRIMARY KEY,
    sku VARCHAR(100) NOT NULL UNIQUE,
    nombre VARCHAR(255) NOT NULL,
    categoria VARCHAR(100) NOT NULL,
    descripcion TEXT,
    precio NUMERIC(10, 2) NOT NULL,
    stock INTEGER NOT NULL,
    imagen_url VARCHAR(500)
);

-- Datos iniciales de supermercado (26 productos)
INSERT INTO productos (sku, nombre, categoria, descripcion, precio, stock, imagen_url) VALUES 
-- LÁCTEOS Y HUEVOS
('SUP-LAC-001', 'Leche Entera Colun 1L', 'LACTEOS_Y_HUEVOS', 'Leche entera pasteurizada en caja 1 Litro', 1190.00, 150, 'https://images.unsplash.com/photo-1563636619-e9143da7973b'),
('SUP-LAC-002', 'Queso Gauda Soprole Laminado 250g', 'LACTEOS_Y_HUEVOS', 'Queso gauda mantecoso laminado 250 gramos', 2890.00, 80, 'https://images.unsplash.com/photo-1486297678162-eb2a19b0a32d'),
('SUP-LAC-003', 'Huevos Blancos L 12 Unidades', 'LACTEOS_Y_HUEVOS', 'Bandeja de 12 huevos frescos de gallina categoría L', 3490.00, 100, 'https://images.unsplash.com/photo-1516448620398-c5f44bf9f441'),
('SUP-LAC-004', 'Yogurt Batido Soprole Frutilla 120g', 'LACTEOS_Y_HUEVOS', 'Yogurt batido sabor frutilla 120g', 390.00, 200, 'https://images.unsplash.com/photo-1571212515416-fef01fc43637'),
('SUP-LAC-005', 'Mantequilla Colun Con Sal 250g', 'LACTEOS_Y_HUEVOS', 'Mantequilla tradicional pasteurizada con sal 250g', 2490.00, 60, 'https://images.unsplash.com/photo-1589985270826-4b7bb135bc9d'),

-- ABARROTES
('SUP-ABA-001', 'Arroz Grado 1 Tucapel 1kg', 'ABARROTES', 'Arroz de grano largo ancho grado 1 paquete 1kg', 1590.00, 200, 'https://images.unsplash.com/photo-1586201375761-83865001e31c'),
('SUP-ABA-002', 'Aceite Vegetal Belmont 1L', 'ABARROTES', 'Aceite vegetal 100% puro 1 Litro', 2290.00, 120, 'https://images.unsplash.com/photo-1474979266404-7eaacbcd87c5'),
('SUP-ABA-003', 'Fideos Tallarines Lucchetti N°5 400g', 'ABARROTES', 'Pasta de sémola de trigo duro paquete 400g', 990.00, 180, 'https://images.unsplash.com/photo-1621996346565-e3d5d6281358'),
('SUP-ABA-004', 'Salsa de Tomate Carozzi Tuco Italiana 200g', 'ABARROTES', 'Salsa de tomate preparada estilo italiano sachet 200g', 690.00, 150, 'https://images.unsplash.com/photo-1572449043416-55f4685c9bb7'),
('SUP-ABA-005', 'Harina con Polvos de Hornear Selecta 1kg', 'ABARROTES', 'Harina de trigo con polvos de hornear 1kg', 1290.00, 110, 'https://images.unsplash.com/photo-1509440159596-0249088772ff'),
('SUP-ABA-006', 'Atún al Agua San José 160g', 'ABARROTES', 'Lomitos de atún en agua lomo entero 160g', 1390.00, 140, 'https://images.unsplash.com/photo-1544551763-46a013bb70d5'),
('SUP-ABA-007', 'Café Nescafé Tradición 170g', 'ABARROTES', 'Café instantáneo en frasco de vidrio 170g', 4990.00, 70, 'https://images.unsplash.com/photo-1559056199-641a0ac8b55e'),
('SUP-ABA-008', 'Azúcar Blanca Iansa 1kg', 'ABARROTES', 'Azúcar granulada refinada grado 1 paquete 1kg', 1190.00, 160, 'https://images.unsplash.com/photo-1581441363689-1f3c3c414635'),

-- BEBIDAS
('SUP-BEB-001', 'Coca-Cola Sabor Original 2.5L', 'BEBIDAS', 'Bebida de fantasía gaseosa botella 2.5 Litros', 2490.00, 180, 'https://images.unsplash.com/photo-1554866585-cd94860890b7'),
('SUP-BEB-002', 'Agua Mineral Cachantun Sin Gas 1.5L', 'BEBIDAS', 'Agua mineral purificada sin gas 1.5 Litros', 990.00, 250, 'https://images.unsplash.com/photo-1548839140-29a749e1bc4e'),
('SUP-BEB-003', 'Néctar Watt''s Durazno 1.5L', 'BEBIDAS', 'Jugo néctar sabor durazno tetra pack 1.5 Litros', 1490.00, 90, 'https://images.unsplash.com/photo-1613478223719-2ab802602423'),
('SUP-BEB-004', 'Cerveza Heineken Pack 6 Latas 354cc', 'BEBIDAS', 'Pack 6 latas cerveza premium lager 354cc', 5990.00, 85, 'https://images.unsplash.com/photo-1608270586620-248524c67de9'),

-- FRUTAS Y VERDURAS
('SUP-FRU-001', 'Manzana Red Delicious 1kg', 'FRUTAS_Y_VERDURAS', 'Manzana roja dulce fresca por kilo', 1890.00, 110, 'https://images.unsplash.com/photo-1560806887-1e4cd0b6cbd6'),
('SUP-FRU-002', 'Plátano Importado 1kg', 'FRUTAS_Y_VERDURAS', 'Plátano cavendish fresco por kilo', 1390.00, 130, 'https://images.unsplash.com/photo-1571771894821-ce9b6c11b08e'),
('SUP-FRU-003', 'Tomate Larga Vida 1kg', 'FRUTAS_Y_VERDURAS', 'Tomate rojo seleccionado fresco por kilo', 1690.00, 95, 'https://images.unsplash.com/photo-1592924357228-91a4daadcfea'),

-- CARNES Y PESCADOS
('SUP-CAR-001', 'Pechuga de Pollo Desosada Ariztía 1kg', 'CARNES_Y_PESCADOS', 'Pechuga de pollo deshuesada fresca al vacío 1kg', 5990.00, 60, 'https://images.unsplash.com/photo-1604503468506-a8da13d82791'),
('SUP-CAR-002', 'Lomo Vetado de Vacuno 1kg', 'CARNES_Y_PESCADOS', 'Corte de vacuno lomo vetado fresco por kilo', 12990.00, 35, 'https://images.unsplash.com/photo-1558030006-450675393462'),

-- PANADERÍA
('SUP-PAN-001', 'Pan Molde Blanco Ideal 560g', 'PANADERIA', 'Pan de molde blanco extra suave en bolsa 560g', 2190.00, 90, 'https://images.unsplash.com/photo-1509440159596-0249088772ff'),

-- LIMPIEZA
('SUP-LIM-001', 'Detergente Líquido Omo Multiacción 3L', 'LIMPIEZA', 'Detergente para ropa concentrado 3 Litros', 8990.00, 45, 'https://images.unsplash.com/photo-1585421514284-efb74c2b69ba'),
('SUP-LIM-002', 'Papel Higiénico Elite Doble Hoja 8 Rollos', 'LIMPIEZA', 'Pack 8 rollos papel higiénico doble hoja suave', 4590.00, 130, 'https://images.unsplash.com/photo-1584556812952-905ffd0c611a'),
('SUP-LIM-003', 'Lavaloza Cif Gel Limón 750ml', 'LIMPIEZA', 'Detergente concentrado lavaloza gel aroma limón 750ml', 1890.00, 110, 'https://images.unsplash.com/photo-1585421514284-efb74c2b69ba')
ON CONFLICT (sku) DO NOTHING;
