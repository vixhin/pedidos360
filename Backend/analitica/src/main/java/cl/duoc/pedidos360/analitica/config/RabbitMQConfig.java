package cl.duoc.pedidos360.analitica.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_EVENTS = "pedidos360.events";
    public static final String QUEUE_ANALYTICS = "analytics.queue";
    public static final String ROUTING_KEY_ALL_EVENTS = "#";

    // Dead Letter Exchange & Queue
    public static final String EXCHANGE_DLX = "pedidos360.dlx";
    public static final String QUEUE_ANALYTICS_DLQ = "analytics.dlq";
    public static final String ROUTING_KEY_ANALYTICS_DLQ = "analytics.dead-letter";

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EXCHANGE_EVENTS, true, false);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(EXCHANGE_DLX, true, false);
    }

    @Bean
    public Queue analyticsQueue() {
        return QueueBuilder.durable(QUEUE_ANALYTICS)
                .withArgument("x-dead-letter-exchange", EXCHANGE_DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY_ANALYTICS_DLQ)
                .build();
    }

    @Bean
    public Queue analyticsDlq() {
        return QueueBuilder.durable(QUEUE_ANALYTICS_DLQ).build();
    }

    @Bean
    public Binding analyticsBinding(Queue analyticsQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(analyticsQueue).to(eventsExchange).with(ROUTING_KEY_ALL_EVENTS);
    }

    @Bean
    public Binding analyticsDlqBinding(Queue analyticsDlq, TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(analyticsDlq).to(deadLetterExchange).with(ROUTING_KEY_ANALYTICS_DLQ);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
