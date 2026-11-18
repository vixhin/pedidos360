package cl.duoc.pedidos360.carrito.controller;

import cl.duoc.pedidos360.carrito.dto.CantidadRequestDTO;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pruebas de seguridad (IDOR): un usuario no puede modificar ítems del carrito de otro. */
@ExtendWith(MockitoExtension.class)
class CarritoIdorTest {

    @Mock
    private CarritoService carritoService;

    @InjectMocks
    private CarritoController carritoController;

    private CarritoItem itemDeOtroUsuario;

    @BeforeEach
    void setUp() {
        itemDeOtroUsuario = new CarritoItem();
        itemDeOtroUsuario.setId(7L);
        itemDeOtroUsuario.setUsuarioId(20L);
        itemDeOtroUsuario.setCantidad(1);
    }

    private MockHttpServletRequest solicitud(Long userId, String role) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("p360.userId", userId);
        request.setAttribute("p360.role", role);
        return request;
    }

    @Test
    @DisplayName("cliente no puede eliminar un ítem del carrito de otro usuario -> 403")
    void clienteNoEliminaItemAjeno() {
        when(carritoService.obtenerPorId(7L)).thenReturn(Optional.of(itemDeOtroUsuario));

        assertThatThrownBy(() -> carritoController.eliminarItem(7L, solicitud(10L, "CLIENTE")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(carritoService, never()).eliminarItem(anyLong());
    }

    @Test
    @DisplayName("cliente no puede cambiar la cantidad de un ítem de otro usuario -> 403")
    void clienteNoModificaItemAjeno() {
        when(carritoService.obtenerPorId(7L)).thenReturn(Optional.of(itemDeOtroUsuario));

        assertThatThrownBy(() -> carritoController.actualizarCantidad(7L, 50, null, solicitud(10L, "CLIENTE")))
                .isInstanceOf(ResponseStatusException.class);
        verify(carritoService, never()).actualizarCantidad(anyLong(), anyInt());
    }

    @Test
    @DisplayName("el dueño sí puede eliminar su ítem")
    void duenoEliminaSuItem() {
        when(carritoService.obtenerPorId(7L)).thenReturn(Optional.of(itemDeOtroUsuario));

        assertThat(carritoController.eliminarItem(7L, solicitud(20L, "CLIENTE")).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        verify(carritoService).eliminarItem(7L);
    }

    @Test
    @DisplayName("ítem inexistente -> 404")
    void itemInexistente() {
        when(carritoService.obtenerPorId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> carritoController.actualizarCantidad(99L, null, new CantidadRequestDTO(2), solicitud(10L, "CLIENTE")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
