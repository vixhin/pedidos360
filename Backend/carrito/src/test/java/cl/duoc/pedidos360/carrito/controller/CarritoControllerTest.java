package cl.duoc.pedidos360.carrito.controller;

import cl.duoc.pedidos360.carrito.entity.CarritoItem;
import cl.duoc.pedidos360.carrito.service.CarritoService;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CarritoControllerTest {

    @Mock
    private CarritoService carritoService;

    @InjectMocks
    private CarritoController carritoController;

    private CarritoItem mockItem;

    @BeforeEach
    void setUp() {
        mockItem = new CarritoItem();
        mockItem.setId(1L);
        mockItem.setUsuarioId(5L);
        mockItem.setCantidad(3);
    }

    @Test
    @DisplayName("obtenerPorUsuario - retorna HTTP 200 con la lista de items")
    void testObtenerPorUsuario() {
        when(carritoService.obtenerPorUsuario(5L)).thenReturn(List.of(mockItem));

        ResponseEntity<List<CarritoItem>> response = carritoController.obtenerPorUsuario(5L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    @DisplayName("agregarItem - retorna HTTP 200 con el item guardado")
    void testAgregarItem() {
        when(carritoService.agregarOActualizar(any(CarritoItem.class))).thenReturn(mockItem);

        ResponseEntity<CarritoItem> response = carritoController.agregarItem(mockItem);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("eliminarItem - retorna HTTP 204 NO CONTENT")
    void testEliminarItem() {
        doNothing().when(carritoService).eliminarItem(1L);

        ResponseEntity<Void> response = carritoController.eliminarItem(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    @DisplayName("vaciarCarrito - retorna HTTP 204 NO CONTENT")
    void testVaciarCarrito() {
        doNothing().when(carritoService).vaciarCarrito(5L);

        ResponseEntity<Void> response = carritoController.vaciarCarrito(5L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
