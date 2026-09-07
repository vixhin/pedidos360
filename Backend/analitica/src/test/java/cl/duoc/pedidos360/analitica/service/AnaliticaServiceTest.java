package cl.duoc.pedidos360.analitica.service;

import cl.duoc.pedidos360.analitica.entity.AnaliticaEvento;
import cl.duoc.pedidos360.analitica.repository.AnaliticaRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnaliticaServiceTest {

    @Mock
    private AnaliticaRepository analiticaRepository;

    @InjectMocks
    private AnaliticaService analiticaService;

    private AnaliticaEvento mockEvento;

    @BeforeEach
    void setUp() {
        mockEvento = new AnaliticaEvento();
        mockEvento.setId(1L);
        mockEvento.setTipoEvento("COMPRA");
        mockEvento.setDescripcion("Compra realizada por usuario 5");
    }

    @Test
    @DisplayName("obtenerTodos - retorna lista completa de eventos")
    void testObtenerTodos() {
        when(analiticaRepository.findAll()).thenReturn(List.of(mockEvento));

        List<AnaliticaEvento> resultado = analiticaService.obtenerTodos();

        assertThat(resultado).hasSize(1);
    }

    @Test
    @DisplayName("obtenerPorTipo - retorna eventos filtrados por tipo")
    void testObtenerPorTipo() {
        when(analiticaRepository.findByTipoEvento("COMPRA")).thenReturn(List.of(mockEvento));

        List<AnaliticaEvento> resultado = analiticaService.obtenerPorTipo("COMPRA");

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).getTipoEvento()).isEqualTo("COMPRA");
    }

    @Test
    @DisplayName("registrarEvento - guarda el evento correctamente")
    void testRegistrarEvento() {
        when(analiticaRepository.save(any(AnaliticaEvento.class))).thenReturn(mockEvento);

        AnaliticaEvento guardado = analiticaService.registrarEvento(mockEvento);

        assertThat(guardado.getId()).isEqualTo(1L);
        verify(analiticaRepository).save(mockEvento);
    }
}
