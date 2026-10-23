-- V4__seed_rabbit_topology.sql
-- Seed versionado de recursos existentes de RabbitMQ para Pedidos360

-- 1. EXCHANGES EXISTENTES
INSERT INTO rabbit_exchange (name, type, durable, auto_delete, internal, protected_resource, active)
VALUES 
    ('pedidos360.events', 'TOPIC', TRUE, FALSE, FALSE, TRUE, TRUE),
    ('pedidos360.dlx', 'TOPIC', TRUE, FALSE, FALSE, TRUE, TRUE),
    ('chat.dlx', 'DIRECT', TRUE, FALSE, FALSE, TRUE, TRUE)
ON CONFLICT (name) DO NOTHING;

-- 2. QUEUES EXISTENTES
-- (Se resuelve dead_letter_exchange_id dinámicamente mediante subquery para mantener integridad referencial)
INSERT INTO rabbit_queue (name, durable, exclusive, auto_delete, dead_letter_exchange_id, dead_letter_routing_key, protected_resource, active)
VALUES 
    ('cart.queue', TRUE, FALSE, FALSE, (SELECT id FROM rabbit_exchange WHERE name = 'pedidos360.dlx'), 'cart.dead-letter', TRUE, TRUE),
    ('cart.dlq', TRUE, FALSE, FALSE, NULL, NULL, TRUE, TRUE),
    ('notifications.queue', TRUE, FALSE, FALSE, (SELECT id FROM rabbit_exchange WHERE name = 'pedidos360.dlx'), 'notifications.dead-letter', TRUE, TRUE),
    ('notifications.dlq', TRUE, FALSE, FALSE, NULL, NULL, TRUE, TRUE),
    ('analytics.queue', TRUE, FALSE, FALSE, (SELECT id FROM rabbit_exchange WHERE name = 'pedidos360.dlx'), 'analytics.dead-letter', TRUE, TRUE),
    ('analytics.dlq', TRUE, FALSE, FALSE, NULL, NULL, TRUE, TRUE),
    ('chat.pedido.estado.queue', TRUE, FALSE, FALSE, (SELECT id FROM rabbit_exchange WHERE name = 'chat.dlx'), 'chat.deadletter', TRUE, TRUE),
    ('chat.dlq', TRUE, FALSE, FALSE, NULL, NULL, TRUE, TRUE)
ON CONFLICT (name) DO NOTHING;

-- 3. BINDINGS EXISTENTES
INSERT INTO rabbit_binding (queue_id, exchange_id, routing_key, protected_resource, active)
VALUES 
    -- cart
    ((SELECT id FROM rabbit_queue WHERE name = 'cart.queue'), (SELECT id FROM rabbit_exchange WHERE name = 'pedidos360.events'), 'pedido.creado', TRUE, TRUE),
    ((SELECT id FROM rabbit_queue WHERE name = 'cart.dlq'), (SELECT id FROM rabbit_exchange WHERE name = 'pedidos360.dlx'), 'cart.dead-letter', TRUE, TRUE),
    -- notifications
    ((SELECT id FROM rabbit_queue WHERE name = 'notifications.queue'), (SELECT id FROM rabbit_exchange WHERE name = 'pedidos360.events'), 'pedido.#', TRUE, TRUE),
    ((SELECT id FROM rabbit_queue WHERE name = 'notifications.queue'), (SELECT id FROM rabbit_exchange WHERE name = 'pedidos360.events'), 'carrito.#', TRUE, TRUE),
    ((SELECT id FROM rabbit_queue WHERE name = 'notifications.queue'), (SELECT id FROM rabbit_exchange WHERE name = 'pedidos360.events'), 'usuario.#', TRUE, TRUE),
    ((SELECT id FROM rabbit_queue WHERE name = 'notifications.dlq'), (SELECT id FROM rabbit_exchange WHERE name = 'pedidos360.dlx'), 'notifications.dead-letter', TRUE, TRUE),
    -- analytics
    ((SELECT id FROM rabbit_queue WHERE name = 'analytics.queue'), (SELECT id FROM rabbit_exchange WHERE name = 'pedidos360.events'), '#', TRUE, TRUE),
    ((SELECT id FROM rabbit_queue WHERE name = 'analytics.dlq'), (SELECT id FROM rabbit_exchange WHERE name = 'pedidos360.dlx'), 'analytics.dead-letter', TRUE, TRUE),
    -- chat
    ((SELECT id FROM rabbit_queue WHERE name = 'chat.pedido.estado.queue'), (SELECT id FROM rabbit_exchange WHERE name = 'pedidos360.events'), 'pedido.estado.actualizado', TRUE, TRUE),
    ((SELECT id FROM rabbit_queue WHERE name = 'chat.dlq'), (SELECT id FROM rabbit_exchange WHERE name = 'chat.dlx'), 'chat.deadletter', TRUE, TRUE)
ON CONFLICT (queue_id, exchange_id, routing_key) DO NOTHING;
