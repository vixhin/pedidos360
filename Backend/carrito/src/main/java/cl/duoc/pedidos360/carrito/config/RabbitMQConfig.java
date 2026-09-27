package cl.duoc.pedidos360.carrito.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_EVENTS = "pedidos360.events";
    public static final String QUEUE_CART = "cart.queue";
    public static final String ROUTING_KEY_PEDIDO_CREADO = "pedido.creado";
    public static final String ROUTING_KEY_CARRITO_EXPIRADO = "carrito.expirado";

    // Dead Letter Exchange & Queue
    public static final String EXCHANGE_DLX = "pedidos360.dlx";
    public static final String QUEUE_CART_DLQ = "cart.dlq";
    public static final String ROUTING_KEY_CART_DLQ = "cart.dead-letter";

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EXCHANGE_EVENTS, true, false);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(EXCHANGE_DLX, true, false);
    }

    @Bean
    public Queue cartQueue() {
        return QueueBuilder.durable(QUEUE_CART)
                .withArgument("x-dead-letter-exchange", EXCHANGE_DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY_CART_DLQ)
                .build();
    }

    @Bean
    public Queue cartDlq() {
        return QueueBuilder.durable(QUEUE_CART_DLQ).build();
    }

    @Bean
    public Binding cartBinding(Queue cartQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(cartQueue).to(eventsExchange).with(ROUTING_KEY_PEDIDO_CREADO);
    }

    @Bean
    public Binding cartDlqBinding(Queue cartDlq, TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(cartDlq).to(deadLetterExchange).with(ROUTING_KEY_CART_DLQ);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }

    @Bean
    public org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate(org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory) {
        org.springframework.amqp.rabbit.core.RabbitTemplate template = new org.springframework.amqp.rabbit.core.RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}
