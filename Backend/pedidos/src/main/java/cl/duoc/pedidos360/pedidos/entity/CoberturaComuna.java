package cl.duoc.pedidos360.pedidos.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "cobertura_comunas")
public class CoberturaComuna {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String nombre;

    @Column(nullable = false, length = 100)
    private String region = "Región Metropolitana";

    @Column(name = "tiempo_estimado_minutos", nullable = false)
    private Integer tiempoEstimadoMinutos = 35;

    @Column(name = "costo_adicional", nullable = false, precision = 10, scale = 2)
    private BigDecimal costoAdicional = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean activa = true;

    public CoberturaComuna() {}

    public CoberturaComuna(Long id, String nombre, String region, Integer tiempoEstimadoMinutos, BigDecimal costoAdicional, boolean activa) {
        this.id = id;
        this.nombre = nombre;
        this.region = region;
        this.tiempoEstimadoMinutos = tiempoEstimadoMinutos;
        this.costoAdicional = costoAdicional;
        this.activa = activa;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public Integer getTiempoEstimadoMinutos() { return tiempoEstimadoMinutos; }
    public void setTiempoEstimadoMinutos(Integer tiempoEstimadoMinutos) { this.tiempoEstimadoMinutos = tiempoEstimadoMinutos; }
    public BigDecimal getCostoAdicional() { return costoAdicional; }
    public void setCostoAdicional(BigDecimal costoAdicional) { this.costoAdicional = costoAdicional; }
    public boolean isActiva() { return activa; }
    public void setActiva(boolean activa) { this.activa = activa; }
}

