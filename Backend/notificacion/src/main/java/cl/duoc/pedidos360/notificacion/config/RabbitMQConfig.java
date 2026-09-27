package cl.duoc.pedidos360.notificacion.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_EVENTS = "pedidos360.events";
    public static final String QUEUE_NOTIFICATIONS = "notifications.queue";
    public static final String ROUTING_KEY_PEDIDO_PATTERN = "pedido.#";
    public static final String ROUTING_KEY_CARRITO_PATTERN = "carrito.#";
    public static final String ROUTING_KEY_USUARIO_PATTERN = "usuario.#";

    // Dead Letter Exchange & Queue
    public static final String EXCHANGE_DLX = "pedidos360.dlx";
    public static final String QUEUE_NOTIFICATIONS_DLQ = "notifications.dlq";
    public static final String ROUTING_KEY_NOTIFICATIONS_DLQ = "notifications.dead-letter";

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EXCHANGE_EVENTS, true, false);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(EXCHANGE_DLX, true, false);
    }

    @Bean
    public Queue notificationsQueue() {
        return QueueBuilder.durable(QUEUE_NOTIFICATIONS)
                .withArgument("x-dead-letter-exchange", EXCHANGE_DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY_NOTIFICATIONS_DLQ)
                .build();
    }

    @Bean
    public Queue notificationsDlq() {
        return QueueBuilder.durable(QUEUE_NOTIFICATIONS_DLQ).build();
    }

    @Bean
    public Binding notificationsPedidoBinding(Queue notificationsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(notificationsQueue).to(eventsExchange).with(ROUTING_KEY_PEDIDO_PATTERN);
    }

    @Bean
    public Binding notificationsCarritoBinding(Queue notificationsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(notificationsQueue).to(eventsExchange).with(ROUTING_KEY_CARRITO_PATTERN);
    }

    @Bean
    public Binding notificationsUsuarioBinding(Queue notificationsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(notificationsQueue).to(eventsExchange).with(ROUTING_KEY_USUARIO_PATTERN);
    }

    @Bean
    public Binding notificationsDlqBinding(Queue notificationsDlq, TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(notificationsDlq).to(deadLetterExchange).with(ROUTING_KEY_NOTIFICATIONS_DLQ);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
