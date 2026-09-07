package cl.duoc.pedidos360.analitica.controller;

import cl.duoc.pedidos360.analitica.entity.AnaliticaEvento;
import cl.duoc.pedidos360.analitica.service.AnaliticaService;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnaliticaControllerTest {

    @Mock
    private AnaliticaService analiticaService;

    @InjectMocks
    private AnaliticaController analiticaController;

    private AnaliticaEvento mockEvento;

    @BeforeEach
    void setUp() {
        mockEvento = new AnaliticaEvento();
        mockEvento.setId(1L);
        mockEvento.setTipoEvento("LOGIN");
    }

    @Test
    @DisplayName("listar - retorna HTTP 200 con la lista de eventos")
    void testListar() {
        when(analiticaService.obtenerTodos()).thenReturn(List.of(mockEvento));

        ResponseEntity<List<AnaliticaEvento>> response = analiticaController.listar();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    @DisplayName("obtenerPorTipo - retorna HTTP 200 con los eventos por tipo")
    void testObtenerPorTipo() {
        when(analiticaService.obtenerPorTipo("LOGIN")).thenReturn(List.of(mockEvento));

        ResponseEntity<List<AnaliticaEvento>> response = analiticaController.obtenerPorTipo("LOGIN");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("registrar - retorna HTTP 200 con evento guardado")
    void testRegistrar() {
        when(analiticaService.registrarEvento(any(AnaliticaEvento.class))).thenReturn(mockEvento);

        ResponseEntity<AnaliticaEvento> response = analiticaController.registrar(mockEvento);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getId()).isEqualTo(1L);
    }
}
