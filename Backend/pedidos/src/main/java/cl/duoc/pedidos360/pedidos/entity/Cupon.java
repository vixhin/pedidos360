package cl.duoc.pedidos360.pedidos.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "cupones")
public class Cupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String codigo;

    @Column(name = "tipo_descuento", nullable = false, length = 20)
    private String tipoDescuento; // 'PORCENTAJE' o 'MONTO_FIJO'

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(name = "monto_minimo", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoMinimo = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(name = "vigente_hasta")
    private LocalDateTime vigenteHasta;

    @Column(name = "usos_maximos")
    private Integer usosMaximos = 1000;

    @Column(name = "usos_actuales")
    private Integer usosActuales = 0;

    @Column(name = "descuento_maximo", precision = 10, scale = 2)
    private BigDecimal descuentoMaximo;

    public Cupon() {}

    public Cupon(Long id, String codigo, String tipoDescuento, BigDecimal valor, BigDecimal montoMinimo, boolean activo) {
        this.id = id;
        this.codigo = codigo;
        this.tipoDescuento = tipoDescuento;
        this.valor = valor;
        this.montoMinimo = montoMinimo;
        this.activo = activo;
        this.usosMaximos = 1000;
        this.usosActuales = 0;
    }

    public boolean isVigente() {
        if (!activo) return false;
        if (vigenteHasta != null && LocalDateTime.now().isAfter(vigenteHasta)) return false;
        if (usosMaximos != null && usosActuales != null && usosActuales >= usosMaximos) return false;
        return true;
    }

    public Integer getUsosMaximos() { return usosMaximos; }
    public void setUsosMaximos(Integer usosMaximos) { this.usosMaximos = usosMaximos; }
    public Integer getUsosActuales() { return usosActuales; }
    public void setUsosActuales(Integer usosActuales) { this.usosActuales = usosActuales; }
    public BigDecimal getDescuentoMaximo() { return descuentoMaximo; }
    public void setDescuentoMaximo(BigDecimal descuentoMaximo) { this.descuentoMaximo = descuentoMaximo; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getTipoDescuento() { return tipoDescuento; }
    public void setTipoDescuento(String tipoDescuento) { this.tipoDescuento = tipoDescuento; }
    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }
    public BigDecimal getMontoMinimo() { return montoMinimo; }
    public void setMontoMinimo(BigDecimal montoMinimo) { this.montoMinimo = montoMinimo; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
    public LocalDateTime getVigenteHasta() { return vigenteHasta; }
    public void setVigenteHasta(LocalDateTime vigenteHasta) { this.vigenteHasta = vigenteHasta; }
}

