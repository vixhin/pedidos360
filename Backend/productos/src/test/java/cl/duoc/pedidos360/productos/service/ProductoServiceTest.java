package cl.duoc.pedidos360.productos.service;

import cl.duoc.pedidos360.productos.dto.ProductoCreateDTO;
import cl.duoc.pedidos360.productos.dto.ProductoResponseDTO;
import cl.duoc.pedidos360.productos.entity.Producto;
import cl.duoc.pedidos360.productos.repository.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductoServiceTest {

    @Mock
    private ProductoRepository productoRepository;

    @InjectMocks
    private ProductoService productoService;

    private Producto mockProducto;

    @BeforeEach
    void setUp() {
        mockProducto = new Producto(1L, "SKU-001", "Leche Colun 1L", "LACTEOS_Y_HUEVOS", "Leche entera 1L", 1190.0, 100, "http://img");
    }

    @Test
    @DisplayName("obtenerTodos - retorna lista completa de productos")
    void testObtenerTodos() {
        when(productoRepository.findAll()).thenReturn(List.of(mockProducto));

        List<ProductoResponseDTO> resultado = productoService.obtenerTodos();

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getSku()).isEqualTo("SKU-001");
    }

    @Test
    @DisplayName("obtenerPorId - retorna producto por ID")
    void testObtenerPorIdExito() {
        when(productoRepository.findById(1L)).thenReturn(Optional.of(mockProducto));

        ProductoResponseDTO dto = productoService.obtenerPorId(1L);

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("obtenerPorId - lanza excepción si no existe")
    void testObtenerPorIdNoEncontrado() {
        when(productoRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> productoService.obtenerPorId(99L));
    }

    @Test
    @DisplayName("obtenerPorCategoria - filtra por categoría")
    void testObtenerPorCategoria() {
        when(productoRepository.findByCategoriaIgnoreCase("LACTEOS_Y_HUEVOS")).thenReturn(List.of(mockProducto));

        List<ProductoResponseDTO> resultado = productoService.obtenerPorCategoria("LACTEOS_Y_HUEVOS");

        assertThat(resultado).hasSize(1);
    }

    @Test
    @DisplayName("buscarPorNombre - mapeo semántico y término")
    void testBuscarPorNombre() {
        when(productoRepository.buscarPorTermino("BEBIDAS")).thenReturn(List.of(mockProducto));

        List<ProductoResponseDTO> resultado = productoService.buscarPorNombre("chela");

        assertThat(resultado).hasSize(1);
    }

    @Test
    @DisplayName("crearProducto - guarda producto correctamente")
    void testCrearProductoExito() {
        ProductoCreateDTO dto = new ProductoCreateDTO("SKU-002", "Pan Molde", "PANADERIA", "Pan 500g", 2000.0, 50, "http://img");
        when(productoRepository.findBySku("SKU-002")).thenReturn(Optional.empty());
        when(productoRepository.save(any(Producto.class))).thenAnswer(i -> {
            Producto p = i.getArgument(0);
            p.setId(2L);
            return p;
        });

        ProductoResponseDTO response = productoService.crearProducto(dto);

        assertThat(response.getId()).isEqualTo(2L);
        assertThat(response.getSku()).isEqualTo("SKU-002");
    }

    @Test
    @DisplayName("crearProducto - lanza excepción por SKU duplicado")
    void testCrearProductoSkuDuplicado() {
        ProductoCreateDTO dto = new ProductoCreateDTO("SKU-001", "Pan", "PANADERIA", "Desc", 1000.0, 10, "img");
        when(productoRepository.findBySku("SKU-001")).thenReturn(Optional.of(mockProducto));

        assertThrows(RuntimeException.class, () -> productoService.crearProducto(dto));
    }

    @Test
    @DisplayName("actualizarProducto - actualiza campos correctamente")
    void testActualizarProductoExito() {
        ProductoCreateDTO dto = new ProductoCreateDTO("SKU-001", "Leche 1L Mod", "LACTEOS", "Desc", 1200.0, 80, "http://img2");
        when(productoRepository.findById(1L)).thenReturn(Optional.of(mockProducto));
        when(productoRepository.save(any(Producto.class))).thenReturn(mockProducto);

        ProductoResponseDTO response = productoService.actualizarProducto(1L, dto);

        assertThat(response).isNotNull();
        verify(productoRepository).save(mockProducto);
    }

    @Test
    @DisplayName("eliminar - elimina producto existente")
    void testEliminarExito() {
        when(productoRepository.existsById(1L)).thenReturn(true);
        doNothing().when(productoRepository).deleteById(1L);

        productoService.eliminar(1L);

        verify(productoRepository).deleteById(1L);
    }
}
