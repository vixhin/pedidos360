package cl.duoc.pedidos360.pedidos.dto;

import cl.duoc.pedidos360.pedidos.entity.CoberturaComuna;
import java.math.BigDecimal;

public class CoberturaComunaDTO {
    private Long id;
    private String nombre;
    private String region;
    private Integer tiempoEstimadoMinutos;
    private BigDecimal costoAdicional;
    private boolean activa;

    public CoberturaComunaDTO() {}

    public static CoberturaComunaDTO fromEntity(CoberturaComuna entity) {
        CoberturaComunaDTO dto = new CoberturaComunaDTO();
        dto.setId(entity.getId());
        dto.setNombre(entity.getNombre());
        dto.setRegion(entity.getRegion());
        dto.setTiempoEstimadoMinutos(entity.getTiempoEstimadoMinutos());
        dto.setCostoAdicional(entity.getCostoAdicional());
        dto.setActiva(entity.isActiva());
        return dto;
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

