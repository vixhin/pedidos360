package cl.duoc.pedidos360.pedidos.repository;

import cl.duoc.pedidos360.pedidos.entity.CoberturaComuna;
import cl.duoc.pedidos360.pedidos.entity.ConfiguracionComercial;
import cl.duoc.pedidos360.pedidos.entity.Cupon;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CuponRepositoryDatabaseTest {

    @Autowired
    private CuponRepository cuponRepository;

    @Autowired
    private ConfiguracionComercialRepository configRepository;

    @Autowired
    private CoberturaComunaRepository coberturaRepository;

    @Test
    @DisplayName("Persistencia y consulta de Cupones en base de datos")
    void testCuponesPersistenciaYFiltros() {
        Cupon c1 = new Cupon(null, "TEST10", "PORCENTAJE", new BigDecimal("10.00"), new BigDecimal("5000.00"), true);
        Cupon c2 = new Cupon(null, "TESTINACTIVO", "MONTO_FIJO", new BigDecimal("500.00"), BigDecimal.ZERO, false);

        cuponRepository.save(c1);
        cuponRepository.save(c2);

        // 1. Búsqueda por código insensible a mayúsculas
        Optional<Cupon> encontrado = cuponRepository.findByCodigoIgnoreCase("test10");
        assertThat(encontrado).isPresent();
        assertThat(encontrado.get().getValor()).isEqualByComparingTo("10.00");
        assertThat(encontrado.get().isActivo()).isTrue();

        // 2. Consulta de activos
        List<Cupon> activos = cuponRepository.findByActivoTrue();
        assertThat(activos.stream().anyMatch(c -> c.getCodigo().equals("TEST10"))).isTrue();
        assertThat(activos.stream().anyMatch(c -> c.getCodigo().equals("TESTINACTIVO"))).isFalse();

        // 3. Modificación del estado (toggle) y persistencia
        c1.setActivo(false);
        cuponRepository.save(c1);

        List<Cupon> despuesDeToggle = cuponRepository.findByActivoTrue();
        assertThat(despuesDeToggle.stream().anyMatch(c -> c.getCodigo().equals("TEST10"))).isFalse();
    }

    @Test
    @DisplayName("Persistencia de ConfiguracionComercial en base de datos")
    void testConfiguracionComercialPersistencia() {
        ConfiguracionComercial config = new ConfiguracionComercial(null, "umbral_test", new BigDecimal("25000.00"), "Umbral test");
        configRepository.save(config);

        Optional<ConfiguracionComercial> encontrada = configRepository.findByClave("umbral_test");
        assertThat(encontrada).isPresent();
        assertThat(encontrada.get().getValor()).isEqualByComparingTo("25000.00");
    }

    @Test
    @DisplayName("Persistencia de CoberturaComuna en base de datos")
    void testCoberturaComunasPersistencia() {
        CoberturaComuna comuna = new CoberturaComuna(null, "TestComuna", "RM", 25, BigDecimal.ZERO, true);
        coberturaRepository.save(comuna);

        Optional<CoberturaComuna> encontrada = coberturaRepository.findByNombreIgnoreCaseAndActivaTrue("testcomuna");
        assertThat(encontrada).isPresent();
        assertThat(encontrada.get().getTiempoEstimadoMinutos()).isEqualTo(25);
    }
}
