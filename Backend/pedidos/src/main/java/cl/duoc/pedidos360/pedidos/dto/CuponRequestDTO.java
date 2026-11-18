package cl.duoc.pedidos360.pedidos.dto;

import cl.duoc.pedidos360.pedidos.entity.Cupon;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Datos para crear un cupón. Excluye id y usosActuales (los controla el servidor).
 * Los campos nulos conservan los valores por defecto de la entidad.
 */
public record CuponRequestDTO(
        String codigo,
        String tipoDescuento,
        BigDecimal valor,
        BigDecimal montoMinimo,
        Boolean activo,
        LocalDateTime vigenteHasta,
        Integer usosMaximos,
        BigDecimal descuentoMaximo) {

    public Cupon toEntity() {
        Cupon cupon = new Cupon();
        cupon.setCodigo(codigo);
        cupon.setTipoDescuento(tipoDescuento);
        cupon.setValor(valor);
        if (montoMinimo != null) {
            cupon.setMontoMinimo(montoMinimo);
        }
        if (activo != null) {
            cupon.setActivo(activo);
        }
        cupon.setVigenteHasta(vigenteHasta);
        if (usosMaximos != null) {
            cupon.setUsosMaximos(usosMaximos);
        }
        cupon.setDescuentoMaximo(descuentoMaximo);
        return cupon;
    }
}
