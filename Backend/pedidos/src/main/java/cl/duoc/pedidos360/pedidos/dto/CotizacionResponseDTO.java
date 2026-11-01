package cl.duoc.pedidos360.pedidos.dto;

import java.math.BigDecimal;

public class CotizacionResponseDTO {
    private BigDecimal subtotal;
    private BigDecimal costoEnvio;
    private BigDecimal tarifaServicio;
    private BigDecimal descuentoCupon;
    private String codigoCupon;
    private String mensajeCupon;
    private BigDecimal total;
    private Integer tiempoEstimadoMinutos;
    private boolean envioGratisAplicado;
    private BigDecimal umbralEnvioGratis;
    private BigDecimal montoFaltanteParaEnvioGratis;
    private String comuna;

    public CotizacionResponseDTO() {}

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public BigDecimal getCostoEnvio() { return costoEnvio; }
    public void setCostoEnvio(BigDecimal costoEnvio) { this.costoEnvio = costoEnvio; }
    public BigDecimal getTarifaServicio() { return tarifaServicio; }
    public void setTarifaServicio(BigDecimal tarifaServicio) { this.tarifaServicio = tarifaServicio; }
    public BigDecimal getDescuentoCupon() { return descuentoCupon; }
    public void setDescuentoCupon(BigDecimal descuentoCupon) { this.descuentoCupon = descuentoCupon; }
    public String getCodigoCupon() { return codigoCupon; }
    public void setCodigoCupon(String codigoCupon) { this.codigoCupon = codigoCupon; }
    public String getMensajeCupon() { return mensajeCupon; }
    public void setMensajeCupon(String mensajeCupon) { this.mensajeCupon = mensajeCupon; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public Integer getTiempoEstimadoMinutos() { return tiempoEstimadoMinutos; }
    public void setTiempoEstimadoMinutos(Integer tiempoEstimadoMinutos) { this.tiempoEstimadoMinutos = tiempoEstimadoMinutos; }
    public boolean isEnvioGratisAplicado() { return envioGratisAplicado; }
    public void setEnvioGratisAplicado(boolean envioGratisAplicado) { this.envioGratisAplicado = envioGratisAplicado; }
    public BigDecimal getUmbralEnvioGratis() { return umbralEnvioGratis; }
    public void setUmbralEnvioGratis(BigDecimal umbralEnvioGratis) { this.umbralEnvioGratis = umbralEnvioGratis; }
    public BigDecimal getMontoFaltanteParaEnvioGratis() { return montoFaltanteParaEnvioGratis; }
    public void setMontoFaltanteParaEnvioGratis(BigDecimal montoFaltanteParaEnvioGratis) { this.montoFaltanteParaEnvioGratis = montoFaltanteParaEnvioGratis; }
    public String getComuna() { return comuna; }
    public void setComuna(String comuna) { this.comuna = comuna; }
}

