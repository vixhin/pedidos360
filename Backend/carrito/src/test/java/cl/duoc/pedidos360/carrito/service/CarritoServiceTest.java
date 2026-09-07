package cl.duoc.pedidos360.carrito.service;

import cl.duoc.pedidos360.carrito.entity.CarritoItem;
import cl.duoc.pedidos360.carrito.repository.CarritoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CarritoServiceTest {

    @Mock
    private CarritoRepository carritoRepository;

    @InjectMocks
    private CarritoService carritoService;

    private CarritoItem mockItem;

    @BeforeEach
    void setUp() {
        mockItem = new CarritoItem();
        mockItem.setId(1L);
        mockItem.setUsuarioId(5L);
        mockItem.setProductoId(101L);
        mockItem.setCantidad(2);
    }

    @Test
    @DisplayName("obtenerPorUsuario - retorna items de carrito del usuario")
    void testObtenerPorUsuario() {
        when(carritoRepository.findByUsuarioId(5L)).thenReturn(List.of(mockItem));

        List<CarritoItem> resultado = carritoService.obtenerPorUsuario(5L);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getCantidad()).isEqualTo(2);
    }

    @Test
    @DisplayName("agregarOActualizar - guarda item correctamente")
    void testAgregarOActualizar() {
        when(carritoRepository.save(any(CarritoItem.class))).thenReturn(mockItem);

        CarritoItem guardado = carritoService.agregarOActualizar(mockItem);

        assertThat(guardado.getId()).isEqualTo(1L);
        verify(carritoRepository).save(mockItem);
    }

    @Test
    @DisplayName("eliminarItem - elimina item por ID")
    void testEliminarItem() {
        doNothing().when(carritoRepository).deleteById(1L);

        carritoService.eliminarItem(1L);

        verify(carritoRepository).deleteById(1L);
    }

    @Test
    @DisplayName("vaciarCarrito - vacia items por ID de usuario")
    void testVaciarCarrito() {
        doNothing().when(carritoRepository).deleteByUsuarioId(5L);

        carritoService.vaciarCarrito(5L);

        verify(carritoRepository).deleteByUsuarioId(5L);
    }
}
