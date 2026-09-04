package cl.duoc.pedidos360.productos.service;

import cl.duoc.pedidos360.productos.dto.ProductoCreateDTO;
import cl.duoc.pedidos360.productos.dto.ProductoResponseDTO;
import cl.duoc.pedidos360.productos.entity.Producto;
import cl.duoc.pedidos360.productos.repository.ProductoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductoService {

    private static final Logger log = LoggerFactory.getLogger(ProductoService.class);

    private final ProductoRepository productoRepository;

    public ProductoService(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initSupermarketProducts() {
        if (productoRepository.count() == 0) {
            log.info("[PRODUCTOS-SERVICE] Seeding 26 supermarket products into PostgreSQL...");
            List<Producto> mockCatalog = Arrays.asList(
                    // LÁCTEOS Y HUEVOS
                    new Producto(null, "SUP-LAC-001", "Leche Entera Colun 1L", "LACTEOS_Y_HUEVOS", "Leche entera pasteurizada en caja 1 Litro", 1190.00, 150, "https://images.unsplash.com/photo-1563636619-e9143da7973b"),
                    new Producto(null, "SUP-LAC-002", "Queso Gauda Soprole Laminado 250g", "LACTEOS_Y_HUEVOS", "Queso gauda mantecoso laminado 250 gramos", 2890.00, 80, "https://images.unsplash.com/photo-1486297678162-eb2a19b0a32d"),
                    new Producto(null, "SUP-LAC-003", "Huevos Blancos L 12 Unidades", "LACTEOS_Y_HUEVOS", "Bandeja de 12 huevos frescos de gallina categoría L", 3490.00, 100, "https://images.unsplash.com/photo-1516448620398-c5f44bf9f441"),
                    new Producto(null, "SUP-LAC-004", "Yogurt Batido Soprole Frutilla 120g", "LACTEOS_Y_HUEVOS", "Yogurt batido sabor frutilla 120g", 390.00, 200, "https://images.unsplash.com/photo-1571212515416-fef01fc43637"),
                    new Producto(null, "SUP-LAC-005", "Mantequilla Colun Con Sal 250g", "LACTEOS_Y_HUEVOS", "Mantequilla tradicional pasteurizada con sal 250g", 2490.00, 60, "https://images.unsplash.com/photo-1589985270826-4b7bb135bc9d"),

                    // ABARROTES
                    new Producto(null, "SUP-ABA-001", "Arroz Grado 1 Tucapel 1kg", "ABARROTES", "Arroz de grano largo ancho grado 1 paquete 1kg", 1590.00, 200, "https://images.unsplash.com/photo-1586201375761-83865001e31c"),
                    new Producto(null, "SUP-ABA-002", "Aceite Vegetal Belmont 1L", "ABARROTES", "Aceite vegetal 100% puro 1 Litro", 2290.00, 120, "https://images.unsplash.com/photo-1474979266404-7eaacbcd87c5"),
                    new Producto(null, "SUP-ABA-003", "Fideos Tallarines Lucchetti N°5 400g", "ABARROTES", "Pasta de sémola de trigo duro paquete 400g", 990.00, 180, "https://images.unsplash.com/photo-1621996346565-e3d5d6281358"),
                    new Producto(null, "SUP-ABA-004", "Salsa de Tomate Carozzi Tuco Italiana 200g", "ABARROTES", "Salsa de tomate preparada estilo italiano sachet 200g", 690.00, 150, "https://images.unsplash.com/photo-1572449043416-55f4685c9bb7"),
                    new Producto(null, "SUP-ABA-005", "Harina con Polvos de Hornear Selecta 1kg", "ABARROTES", "Harina de trigo con polvos de hornear 1kg", 1290.00, 110, "https://images.unsplash.com/photo-1509440159596-0249088772ff"),
                    new Producto(null, "SUP-ABA-006", "Atún al Agua San José 160g", "ABARROTES", "Lomitos de atún en agua lomo entero 160g", 1390.00, 140, "https://images.unsplash.com/photo-1544551763-46a013bb70d5"),
                    new Producto(null, "SUP-ABA-007", "Café Nescafé Tradición 170g", "ABARROTES", "Café instantáneo en frasco de vidrio 170g", 4990.00, 70, "https://images.unsplash.com/photo-1559056199-641a0ac8b55e"),
                    new Producto(null, "SUP-ABA-008", "Azúcar Blanca Iansa 1kg", "ABARROTES", "Azúcar granulada refinada grado 1 paquete 1kg", 1190.00, 160, "https://images.unsplash.com/photo-1581441363689-1f3c3c414635"),

                    // BEBIDAS
                    new Producto(null, "SUP-BEB-001", "Coca-Cola Sabor Original 2.5L", "BEBIDAS", "Bebida de fantasía gaseosa botella 2.5 Litros", 2490.00, 180, "https://images.unsplash.com/photo-1554866585-cd94860890b7"),
                    new Producto(null, "SUP-BEB-002", "Agua Mineral Cachantun Sin Gas 1.5L", "BEBIDAS", "Agua mineral purificada sin gas 1.5 Litros", 990.00, 250, "https://images.unsplash.com/photo-1548839140-29a749e1bc4e"),
                    new Producto(null, "SUP-BEB-003", "Néctar Watt's Durazno 1.5L", "BEBIDAS", "Jugo néctar sabor durazno tetra pack 1.5 Litros", 1490.00, 90, "https://images.unsplash.com/photo-1613478223719-2ab802602423"),
                    new Producto(null, "SUP-BEB-004", "Cerveza Heineken Pack 6 Latas 354cc", "BEBIDAS", "Pack 6 latas cerveza premium lager 354cc", 5990.00, 85, "https://images.unsplash.com/photo-1608270586620-248524c67de9"),

                    // FRUTAS Y VERDURAS
                    new Producto(null, "SUP-FRU-001", "Manzana Red Delicious 1kg", "FRUTAS_Y_VERDURAS", "Manzana roja dulce fresca por kilo", 1890.00, 110, "https://images.unsplash.com/photo-1560806887-1e4cd0b6cbd6"),
                    new Producto(null, "SUP-FRU-002", "Plátano Importado 1kg", "FRUTAS_Y_VERDURAS", "Plátano cavendish fresco por kilo", 1390.00, 130, "https://images.unsplash.com/photo-1571771894821-ce9b6c11b08e"),
                    new Producto(null, "SUP-FRU-003", "Tomate Larga Vida 1kg", "FRUTAS_Y_VERDURAS", "Tomate rojo seleccionado fresco por kilo", 1690.00, 95, "https://images.unsplash.com/photo-1592924357228-91a4daadcfea"),

                    // CARNES Y PESCADOS
                    new Producto(null, "SUP-CAR-001", "Pechuga de Pollo Desosada Ariztía 1kg", "CARNES_Y_PESCADOS", "Pechuga de pollo deshuesada fresca al vacío 1kg", 5990.00, 60, "https://images.unsplash.com/photo-1604503468506-a8da13d82791"),
                    new Producto(null, "SUP-CAR-002", "Lomo Vetado de Vacuno 1kg", "CARNES_Y_PESCADOS", "Corte de vacuno lomo vetado fresco por kilo", 12990.00, 35, "https://images.unsplash.com/photo-1558030006-450675393462"),

                    // PANADERÍA
                    new Producto(null, "SUP-PAN-001", "Pan Molde Blanco Ideal 560g", "PANADERIA", "Pan de molde blanco extra suave en bolsa 560g", 2190.00, 90, "https://images.unsplash.com/photo-1509440159596-0249088772ff"),

                    // LIMPIEZA
                    new Producto(null, "SUP-LIM-001", "Detergente Líquido Omo Multiacción 3L", "LIMPIEZA", "Detergente para ropa concentrado 3 Litros", 8990.00, 45, "https://images.unsplash.com/photo-1585421514284-efb74c2b69ba"),
                    new Producto(null, "SUP-LIM-002", "Papel Higiénico Elite Doble Hoja 8 Rollos", "LIMPIEZA", "Pack 8 rollos papel higiénico doble hoja suave", 4590.00, 130, "https://images.unsplash.com/photo-1584556812952-905ffd0c611a"),
                    new Producto(null, "SUP-LIM-003", "Lavaloza Cif Gel Limón 750ml", "LIMPIEZA", "Detergente concentrado lavaloza gel aroma limón 750ml", 1890.00, 110, "https://images.unsplash.com/photo-1585421514284-efb74c2b69ba")
            );
            productoRepository.saveAll(mockCatalog);
            log.info("[PRODUCTOS-SERVICE] 26 supermarket products seeded successfully!");
        }
    }

    @Transactional(readOnly = true)
    public List<ProductoResponseDTO> obtenerTodos() {
        log.info("[PRODUCTOS-SERVICE] Listing all supermarket products");
        return productoRepository.findAll().stream()
                .map(ProductoResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ProductoResponseDTO obtenerPorId(Long id) {
        log.info("[PRODUCTOS-SERVICE] Fetching product ID={}", id);
        Producto p = productoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado con ID: " + id));
        return ProductoResponseDTO.fromEntity(p);
    }

    @Transactional(readOnly = true)
    public List<ProductoResponseDTO> obtenerPorCategoria(String categoria) {
        log.info("[PRODUCTOS-SERVICE] Filtering products by category={}", categoria);
        return productoRepository.findByCategoriaIgnoreCase(categoria).stream()
                .map(ProductoResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProductoResponseDTO> buscarPorNombre(String nombre) {
        log.info("[PRODUCTOS-SERVICE] Searching products by name={}", nombre);
        return productoRepository.findByNombreContainingIgnoreCase(nombre).stream()
                .map(ProductoResponseDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public ProductoResponseDTO crearProducto(ProductoCreateDTO dto) {
        log.info("[PRODUCTOS-SERVICE] Creating product SKU={} Name={}", dto.getSku(), dto.getNombre());
        if (productoRepository.findBySku(dto.getSku()).isPresent()) {
            log.warn("[PRODUCTOS-SERVICE] Duplicate SKU creation attempt: {}", dto.getSku());
            throw new RuntimeException("El SKU '" + dto.getSku() + "' ya se encuentra registrado");
        }

        Producto p = new Producto(
                null,
                dto.getSku(),
                dto.getNombre(),
                dto.getCategoria(),
                dto.getDescripcion(),
                dto.getPrecio(),
                dto.getStock(),
                dto.getImagenUrl()
        );

        Producto guardado = productoRepository.save(p);
        log.info("[PRODUCTOS-SERVICE] Product created successfully ID={} SKU={}", guardado.getId(), guardado.getSku());
        return ProductoResponseDTO.fromEntity(guardado);
    }

    @Transactional
    public ProductoResponseDTO actualizarProducto(Long id, ProductoCreateDTO dto) {
        log.info("[PRODUCTOS-SERVICE] Updating product ID={}", id);
        Producto p = productoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado con ID: " + id));

        if (!p.getSku().equalsIgnoreCase(dto.getSku()) &&
                productoRepository.findBySku(dto.getSku()).isPresent()) {
            throw new RuntimeException("El SKU '" + dto.getSku() + "' ya se encuentra registrado");
        }

        p.setSku(dto.getSku());
        p.setNombre(dto.getNombre());
        p.setCategoria(dto.getCategoria());
        p.setDescripcion(dto.getDescripcion());
        p.setPrecio(dto.getPrecio());
        p.setStock(dto.getStock());
        if (dto.getImagenUrl() != null) {
            p.setImagenUrl(dto.getImagenUrl());
        }

        Producto actualizado = productoRepository.save(p);
        log.info("[PRODUCTOS-SERVICE] Product updated successfully ID={}", actualizado.getId());
        return ProductoResponseDTO.fromEntity(actualizado);
    }

    @Transactional
    public void eliminar(Long id) {
        log.info("[PRODUCTOS-SERVICE] Deleting product ID={}", id);
        if (!productoRepository.existsById(id)) {
            throw new RuntimeException("Producto no encontrado con ID: " + id);
        }
        productoRepository.deleteById(id);
        log.info("[PRODUCTOS-SERVICE] Product deleted successfully ID={}", id);
    }
}
