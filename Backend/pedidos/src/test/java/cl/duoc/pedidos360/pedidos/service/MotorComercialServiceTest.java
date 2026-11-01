package cl.duoc.pedidos360.pedidos.service;

import cl.duoc.pedidos360.pedidos.dto.CoberturaComunaDTO;
import cl.duoc.pedidos360.pedidos.dto.CotizacionRequestDTO;
import cl.duoc.pedidos360.pedidos.dto.CotizacionResponseDTO;
import cl.duoc.pedidos360.pedidos.entity.CoberturaComuna;
import cl.duoc.pedidos360.pedidos.entity.ConfiguracionComercial;
import cl.duoc.pedidos360.pedidos.entity.Cupon;
import cl.duoc.pedidos360.pedidos.repository.CoberturaComunaRepository;
import cl.duoc.pedidos360.pedidos.repository.ConfiguracionComercialRepository;
import cl.duoc.pedidos360.pedidos.repository.CuponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MotorComercialServiceTest {

    @Mock
    private ConfiguracionComercialRepository configRepository;

    @Mock
    private CuponRepository cuponRepository;

    @Mock
    private CoberturaComunaRepository coberturaRepository;

    @InjectMocks
    private MotorComercialService motorComercialService;

    @BeforeEach
    void setUp() {
        lenient().when(configRepository.findByClaveIgnoreCase("UMBRAL_ENVIO_GRATIS"))
                .thenReturn(Optional.of(new ConfiguracionComercial(1L, "UMBRAL_ENVIO_GRATIS", new BigDecimal("20000.00"), "")));
        lenient().when(configRepository.findByClaveIgnoreCase("COSTO_ENVIO_BASE"))
                .thenReturn(Optional.of(new ConfiguracionComercial(2L, "COSTO_ENVIO_BASE", new BigDecimal("2990.00"), "")));
        lenient().when(configRepository.findByClaveIgnoreCase("TARIFA_SERVICIO"))
                .thenReturn(Optional.of(new ConfiguracionComercial(3L, "TARIFA_SERVICIO", new BigDecimal("1500.00"), "")));
    }

    @Test
    @DisplayName("cotizar - orden bajo umbral aplica costo de envío estándar y tarifa de servicio")
    void testCotizarBajoUmbral() {
        CotizacionRequestDTO request = new CotizacionRequestDTO();
        request.setItems(List.of(
                new CotizacionRequestDTO.ItemCotizacion(1L, "Producto A", 2, new BigDecimal("5000.00")) // subtotal 10000
        ));
        request.setComuna("Santiago");

        when(coberturaRepository.findByNombreIgnoreCaseAndActivaTrue("Santiago"))
                .thenReturn(Optional.of(new CoberturaComuna(1L, "Santiago", "RM", 30, BigDecimal.ZERO, true)));

        CotizacionResponseDTO response = motorComercialService.cotizar(request);

        assertThat(response.getSubtotal()).isEqualByComparingTo("10000.00");
        assertThat(response.isEnvioGratisAplicado()).isFalse();
        assertThat(response.getCostoEnvio()).isEqualByComparingTo("2990.00");
        assertThat(response.getTarifaServicio()).isEqualByComparingTo("1500.00");
        assertThat(response.getMontoFaltanteParaEnvioGratis()).isEqualByComparingTo("10000.00");
        assertThat(response.getTotal()).isEqualByComparingTo("14490.00");
        assertThat(response.getTiempoEstimadoMinutos()).isEqualTo(30);
    }

    @Test
    @DisplayName("cotizar - orden igual o sobre umbral aplica envío gratis")
    void testCotizarSobreUmbralEnvioGratis() {
        CotizacionRequestDTO request = new CotizacionRequestDTO();
        request.setItems(List.of(
                new CotizacionRequestDTO.ItemCotizacion(1L, "Producto B", 5, new BigDecimal("5000.00")) // subtotal 25000
        ));

        CotizacionResponseDTO response = motorComercialService.cotizar(request);

        assertThat(response.getSubtotal()).isEqualByComparingTo("25000.00");
        assertThat(response.isEnvioGratisAplicado()).isTrue();
        assertThat(response.getCostoEnvio()).isEqualByComparingTo("0.00");
        assertThat(response.getTarifaServicio()).isEqualByComparingTo("1500.00");
        assertThat(response.getTotal()).isEqualByComparingTo("26500.00");
    }

    @Test
    @DisplayName("cotizar - aplica cupón porcentual válido correctamente")
    void testCotizarCuponValido() {
        CotizacionRequestDTO request = new CotizacionRequestDTO();
        request.setItems(List.of(
                new CotizacionRequestDTO.ItemCotizacion(1L, "Producto C", 1, new BigDecimal("10000.00"))
        ));
        request.setCodigoCupon("BIENVENIDA360");

        Cupon cupon = new Cupon(1L, "BIENVENIDA360", "PORCENTAJE", new BigDecimal("10.00"), new BigDecimal("5000.00"), true);
        when(cuponRepository.findByCodigoIgnoreCaseAndActivoTrue("BIENVENIDA360"))
                .thenReturn(Optional.of(cupon));

        CotizacionResponseDTO response = motorComercialService.cotizar(request);

        assertThat(response.getDescuentoCupon()).isEqualByComparingTo("1000.00"); // 10% de 10000
        assertThat(response.getCodigoCupon()).isEqualTo("BIENVENIDA360");
        assertThat(response.getTotal()).isEqualByComparingTo("13490.00");
    }

    @Test
    @DisplayName("obtenerComunasActivas - retorna comunas habilitadas")
    void testObtenerComunasActivas() {
        when(coberturaRepository.findByActivaTrue())
                .thenReturn(List.of(new CoberturaComuna(1L, "Las Condes", "RM", 35, BigDecimal.ZERO, true)));

        List<CoberturaComunaDTO> comunas = motorComercialService.obtenerComunasActivas();

        assertThat(comunas).hasSize(1);
        assertThat(comunas.get(0).getNombre()).isEqualTo("Las Condes");
        assertThat(comunas.get(0).getTiempoEstimadoMinutos()).isEqualTo(35);
    }
}
