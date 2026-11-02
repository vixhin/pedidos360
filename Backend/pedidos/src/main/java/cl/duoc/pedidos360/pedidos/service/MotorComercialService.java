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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class MotorComercialService {

    private static final Logger log = LoggerFactory.getLogger(MotorComercialService.class);

    private final ConfiguracionComercialRepository configRepository;
    private final CuponRepository cuponRepository;
    private final CoberturaComunaRepository coberturaRepository;

    public MotorComercialService(ConfiguracionComercialRepository configRepository,
                                 CuponRepository cuponRepository,
                                 CoberturaComunaRepository coberturaRepository) {
        this.configRepository = configRepository;
        this.cuponRepository = cuponRepository;
        this.coberturaRepository = coberturaRepository;
    }

    private String normalizarClave(String clave) {
        if (clave == null) return "";
        String upper = clave.trim().toUpperCase();
        if ("TARIFA_SERVICIO_BASE".equals(upper)) {
            return "TARIFA_SERVICIO";
        }
        return upper;
    }

    public BigDecimal obtenerConfig(String clave, BigDecimal defaultVal) {
        String normalized = normalizarClave(clave);
        return configRepository.findByClaveIgnoreCase(normalized)
                .or(() -> configRepository.findByClaveIgnoreCase(clave))
                .map(ConfiguracionComercial::getValor)
                .orElse(defaultVal);
    }

    @Transactional(readOnly = true)
    public List<CoberturaComunaDTO> obtenerComunasActivas() {
        return coberturaRepository.findByActivaTrue().stream()
                .map(CoberturaComunaDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CotizacionResponseDTO cotizar(CotizacionRequestDTO request) {
        // Cálculo del subtotal basado en los ítems cotizados
        BigDecimal subtotal = BigDecimal.ZERO;
        if (request != null && request.getItems() != null) {
            for (CotizacionRequestDTO.ItemCotizacion item : request.getItems()) {
                if (item.getPrecioUnitario() != null && item.getCantidad() != null && item.getCantidad() > 0) {
                    BigDecimal itemTotal = item.getPrecioUnitario().multiply(BigDecimal.valueOf(item.getCantidad()));
                    subtotal = subtotal.add(itemTotal);
                }
            }
        }

        // Obtención de parámetros comerciales autoritativos de BD
        BigDecimal umbralEnvioGratis = obtenerConfig("UMBRAL_ENVIO_GRATIS", new BigDecimal("20000.00"));
        BigDecimal costoEnvioBase = obtenerConfig("COSTO_ENVIO_BASE", new BigDecimal("2990.00"));
        BigDecimal tarifaServicio = obtenerConfig("TARIFA_SERVICIO", new BigDecimal("1500.00"));

        BigDecimal costoEnvio = costoEnvioBase;
        boolean envioGratis = false;
        BigDecimal faltanteParaEnvioGratis = BigDecimal.ZERO;
        Integer tiempoEstimado = 35;
        String nombreComuna = (request != null && request.getComuna() != null) ? request.getComuna().trim() : null;

        // Evaluación de comuna
        if (nombreComuna != null && !nombreComuna.isBlank()) {
            Optional<CoberturaComuna> comunaOpt = coberturaRepository.findByNombreIgnoreCaseAndActivaTrue(nombreComuna);
            if (comunaOpt.isPresent()) {
                CoberturaComuna comuna = comunaOpt.get();
                costoEnvio = costoEnvio.add(comuna.getCostoAdicional());
                tiempoEstimado = comuna.getTiempoEstimadoMinutos();
            }
        }

        // Evaluación de umbral de envío gratis
        if (subtotal.compareTo(umbralEnvioGratis) >= 0) {
            costoEnvio = BigDecimal.ZERO;
            envioGratis = true;
        } else {
            faltanteParaEnvioGratis = umbralEnvioGratis.subtract(subtotal);
            if (faltanteParaEnvioGratis.compareTo(BigDecimal.ZERO) < 0) {
                faltanteParaEnvioGratis = BigDecimal.ZERO;
            }
        }

        // Evaluación de cupón
        BigDecimal descuentoCupon = BigDecimal.ZERO;
        String codigoAplicado = null;
        String mensajeCupon = null;

        if (request != null && request.getCodigoCupon() != null && !request.getCodigoCupon().trim().isBlank()) {
            String codigo = request.getCodigoCupon().trim().toUpperCase();
            Optional<Cupon> cuponOpt = cuponRepository.findByCodigoIgnoreCaseAndActivoTrue(codigo);

            if (cuponOpt.isPresent()) {
                Cupon cupon = cuponOpt.get();
                boolean vigente = (cupon.getVigenteHasta() == null || cupon.getVigenteHasta().isAfter(LocalDateTime.now()));

                if (!vigente) {
                    mensajeCupon = "El cupón ha expirado";
                } else if (cupon.getUsosMaximos() != null && cupon.getUsosActuales() >= cupon.getUsosMaximos()) {
                    mensajeCupon = "El cupón ha alcanzado el límite de usos permitidos";
                } else if (subtotal.compareTo(cupon.getMontoMinimo()) < 0) {
                    mensajeCupon = "El monto mínimo para aplicar este cupón es $" + cupon.getMontoMinimo().intValue();
                } else {
                    if ("PORCENTAJE".equalsIgnoreCase(cupon.getTipoDescuento())) {
                        descuentoCupon = subtotal.multiply(cupon.getValor())
                                .divide(new BigDecimal("100"), 0, RoundingMode.HALF_UP);
                        if (cupon.getDescuentoMaximo() != null && descuentoCupon.compareTo(cupon.getDescuentoMaximo()) > 0) {
                            descuentoCupon = cupon.getDescuentoMaximo();
                        }
                    } else {
                        descuentoCupon = cupon.getValor();
                    }
                    if (descuentoCupon.compareTo(subtotal) > 0) {
                        descuentoCupon = subtotal;
                    }
                    codigoAplicado = cupon.getCodigo();
                    mensajeCupon = "Cupón '" + cupon.getCodigo() + "' aplicado exitosamente (-$" + descuentoCupon.intValue() + ")";
                }
            } else {
                mensajeCupon = "El cupón ingresado no es válido o está inactivo";
            }
        }

        BigDecimal total = subtotal.add(costoEnvio).add(tarifaServicio).subtract(descuentoCupon);
        if (total.compareTo(BigDecimal.ZERO) < 0) {
            total = BigDecimal.ZERO;
        }

        CotizacionResponseDTO response = new CotizacionResponseDTO();
        response.setSubtotal(subtotal);
        response.setCostoEnvio(costoEnvio);
        response.setTarifaServicio(tarifaServicio);
        response.setDescuentoCupon(descuentoCupon);
        response.setCodigoCupon(codigoAplicado);
        response.setMensajeCupon(mensajeCupon);
        response.setTotal(total);
        response.setTiempoEstimadoMinutos(tiempoEstimado);
        response.setEnvioGratisAplicado(envioGratis);
        response.setUmbralEnvioGratis(umbralEnvioGratis);
        response.setMontoFaltanteParaEnvioGratis(faltanteParaEnvioGratis);
        response.setComuna(nombreComuna);

        return response;
    }

    @Transactional(readOnly = true)
    public List<Cupon> listarCupones(boolean soloActivos) {
        if (soloActivos) {
            return cuponRepository.findByActivoTrue();
        }
        return cuponRepository.findAll();
    }

    @Transactional
    public Cupon crearCupon(Cupon cupon) {
        if (cupon.getCodigo() != null) {
            cupon.setCodigo(cupon.getCodigo().trim().toUpperCase());
        }
        return cuponRepository.save(cupon);
    }

    @Transactional
    public Cupon toggleCupon(Long id) {
        Cupon c = cuponRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cupón no encontrado con ID: " + id));
        c.setActivo(!c.isActivo());
        return cuponRepository.save(c);
    }

    @Transactional(readOnly = true)
    public List<ConfiguracionComercial> listarConfiguraciones() {
        return configRepository.findAll();
    }

    @Transactional
    public ConfiguracionComercial actualizarConfiguracion(String clave, BigDecimal valor) {
        String normalized = normalizarClave(clave);
        ConfiguracionComercial cfg = configRepository.findByClaveIgnoreCase(normalized)
                .or(() -> configRepository.findByClaveIgnoreCase(clave))
                .orElseGet(() -> new ConfiguracionComercial(null, normalized, valor, "Configuración " + normalized));
        cfg.setValor(valor);
        cfg.setActualizadoEl(LocalDateTime.now());
        return configRepository.save(cfg);
    }
}
