package cl.duoc.pedidos360.usuario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class RabbitBindingDTO {

    private Long id;

    @NotNull(message = "El queueId es obligatorio")
    private Long queueId;

    @NotNull(message = "El exchangeId es obligatorio")
    private Long exchangeId;

    @NotBlank(message = "El routingKey es obligatorio")
    @Size(min = 1, max = 255, message = "El routingKey debe tener entre 1 y 255 caracteres")
    private String routingKey;

    private Boolean protectedResource = false;
    private Boolean active = true;

    public RabbitBindingDTO() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getQueueId() {
        return queueId;
    }

    public void setQueueId(Long queueId) {
        this.queueId = queueId;
    }

    public Long getExchangeId() {
        return exchangeId;
    }

    public void setExchangeId(Long exchangeId) {
        this.exchangeId = exchangeId;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public void setRoutingKey(String routingKey) {
        this.routingKey = routingKey;
    }

    public Boolean getProtectedResource() {
        return protectedResource;
    }

    public void setProtectedResource(Boolean protectedResource) {
        this.protectedResource = protectedResource;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
