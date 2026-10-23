package cl.duoc.pedidos360.usuario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RabbitQueueDTO {

    private Long id;

    @NotBlank(message = "El nombre de la queue es obligatorio")
    @Size(min = 2, max = 255, message = "El nombre debe tener entre 2 y 255 caracteres")
    private String name;

    private Boolean durable = true;
    private Boolean exclusive = false;
    private Boolean autoDelete = false;

    private Long deadLetterExchangeId;
    private String deadLetterRoutingKey;

    private Boolean protectedResource = false;
    private Boolean active = true;

    public RabbitQueueDTO() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getDurable() {
        return durable;
    }

    public void setDurable(Boolean durable) {
        this.durable = durable;
    }

    public Boolean getExclusive() {
        return exclusive;
    }

    public void setExclusive(Boolean exclusive) {
        this.exclusive = exclusive;
    }

    public Boolean getAutoDelete() {
        return autoDelete;
    }

    public void setAutoDelete(Boolean autoDelete) {
        this.autoDelete = autoDelete;
    }

    public Long getDeadLetterExchangeId() {
        return deadLetterExchangeId;
    }

    public void setDeadLetterExchangeId(Long deadLetterExchangeId) {
        this.deadLetterExchangeId = deadLetterExchangeId;
    }

    public String getDeadLetterRoutingKey() {
        return deadLetterRoutingKey;
    }

    public void setDeadLetterRoutingKey(String deadLetterRoutingKey) {
        this.deadLetterRoutingKey = deadLetterRoutingKey;
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
