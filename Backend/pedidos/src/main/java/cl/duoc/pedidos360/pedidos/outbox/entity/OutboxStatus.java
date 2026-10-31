package cl.duoc.pedidos360.pedidos.outbox.entity;

public enum OutboxStatus {
    PENDING,
    PROCESSING,
    PUBLISHED,
    FAILED
}
