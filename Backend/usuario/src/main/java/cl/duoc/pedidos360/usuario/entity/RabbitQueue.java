package cl.duoc.pedidos360.usuario.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "rabbit_queue")
public class RabbitQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private Boolean durable = true;

    @Column(nullable = false)
    private Boolean exclusive = false;

    @Column(name = "auto_delete", nullable = false)
    private Boolean autoDelete = false;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "dead_letter_exchange_id")
    private RabbitExchange deadLetterExchange;

    @Column(name = "dead_letter_routing_key")
    private String deadLetterRoutingKey;

    @Column(name = "protected_resource", nullable = false)
    private Boolean protectedResource = false;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public RabbitQueue() {}

    public RabbitQueue(Long id, String name, Boolean durable, Boolean exclusive, Boolean autoDelete,
                       RabbitExchange deadLetterExchange, String deadLetterRoutingKey,
                       Boolean protectedResource, Boolean active) {
        this.id = id;
        this.name = name;
        this.durable = durable != null ? durable : true;
        this.exclusive = exclusive != null ? exclusive : false;
        this.autoDelete = autoDelete != null ? autoDelete : false;
        this.deadLetterExchange = deadLetterExchange;
        this.deadLetterRoutingKey = deadLetterRoutingKey;
        this.protectedResource = protectedResource != null ? protectedResource : false;
        this.active = active != null ? active : true;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

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

    public RabbitExchange getDeadLetterExchange() {
        return deadLetterExchange;
    }

    public void setDeadLetterExchange(RabbitExchange deadLetterExchange) {
        this.deadLetterExchange = deadLetterExchange;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
