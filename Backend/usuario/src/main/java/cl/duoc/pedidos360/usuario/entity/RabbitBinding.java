package cl.duoc.pedidos360.usuario.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "rabbit_binding", uniqueConstraints = {
        @UniqueConstraint(name = "uq_rabbit_binding", columnNames = {"queue_id", "exchange_id", "routing_key"})
})
public class RabbitBinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "queue_id", nullable = false)
    private RabbitQueue queue;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "exchange_id", nullable = false)
    private RabbitExchange exchange;

    @Column(name = "routing_key", nullable = false)
    private String routingKey;

    @Column(name = "protected_resource", nullable = false)
    private Boolean protectedResource = false;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public RabbitBinding() {}

    public RabbitBinding(Long id, RabbitQueue queue, RabbitExchange exchange, String routingKey,
                         Boolean protectedResource, Boolean active) {
        this.id = id;
        this.queue = queue;
        this.exchange = exchange;
        this.routingKey = routingKey;
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

    public RabbitQueue getQueue() {
        return queue;
    }

    public void setQueue(RabbitQueue queue) {
        this.queue = queue;
    }

    public RabbitExchange getExchange() {
        return exchange;
    }

    public void setExchange(RabbitExchange exchange) {
        this.exchange = exchange;
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
