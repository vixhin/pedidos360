package cl.duoc.pedidos360.productos.controller;

import cl.duoc.pedidos360.productos.dto.ApiResponse;
import cl.duoc.pedidos360.productos.dto.ProductoCreateDTO;
import cl.duoc.pedidos360.productos.dto.ProductoResponseDTO;
import cl.duoc.pedidos360.productos.service.ProductoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductoControllerTest {

    @Mock
    private ProductoService productoService;

    @InjectMocks
    private ProductoController productoController;

    private ProductoResponseDTO mockDto;

    @BeforeEach
    void setUp() {
        mockDto = new ProductoResponseDTO(1L, "SKU-1", "Leche", "LACTEOS", "1L", 1000.0, 10, "img");
    }

    @Test
    @DisplayName("listar - retorna HTTP 200 con la lista de productos")
    void testListar() {
        when(productoService.obtenerTodos()).thenReturn(List.of(mockDto));

        ResponseEntity<ApiResponse<List<ProductoResponseDTO>>> response = productoController.listar();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).hasSize(1);
    }

    @Test
    @DisplayName("obtenerPorId - retorna HTTP 200 con el producto")
    void testObtenerPorId() {
        when(productoService.obtenerPorId(1L)).thenReturn(mockDto);

        ResponseEntity<ApiResponse<ProductoResponseDTO>> response = productoController.obtenerPorId(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("obtenerPorCategoria - retorna HTTP 200")
    void testObtenerPorCategoria() {
        when(productoService.obtenerPorCategoria("LACTEOS")).thenReturn(List.of(mockDto));

        ResponseEntity<ApiResponse<List<ProductoResponseDTO>>> response = productoController.obtenerPorCategoria("LACTEOS");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

    }

    @Test
    @DisplayName("buscarPorNombre - retorna HTTP 200")
    void testBuscarPorNombre() {
        when(productoService.buscarPorNombre("Leche")).thenReturn(List.of(mockDto));

        ResponseEntity<ApiResponse<List<ProductoResponseDTO>>> response = productoController.buscarPorNombre("Leche");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("crear - retorna HTTP 201 CREATED")
    void testCrear() {
        ProductoCreateDTO createDto = new ProductoCreateDTO("SKU-1", "Leche", "LACTEOS", "1L", 1000.0, 10, "img");
        when(productoService.crearProducto(any(ProductoCreateDTO.class))).thenReturn(mockDto);

        ResponseEntity<ApiResponse<ProductoResponseDTO>> response = productoController.crear(createDto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("actualizar - retorna HTTP 200 OK")
    void testActualizar() {
        ProductoCreateDTO createDto = new ProductoCreateDTO("SKU-1", "Leche", "LACTEOS", "1L", 1000.0, 10, "img");
        when(productoService.actualizarProducto(eq(1L), any(ProductoCreateDTO.class))).thenReturn(mockDto);

        ResponseEntity<ApiResponse<ProductoResponseDTO>> response = productoController.actualizar(1L, createDto);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("eliminar - retorna HTTP 200 OK")
    void testEliminar() {
        doNothing().when(productoService).eliminar(1L);

        ResponseEntity<ApiResponse<Void>> response = productoController.eliminar(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
