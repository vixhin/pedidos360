package cl.duoc.pedidos360.chat.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_EVENTS = "pedidos360.events";
    public static final String EXCHANGE_DLX = "chat.dlx";

    public static final String QUEUE_CHAT_PEDIDOS_ESTADO = "chat.pedido.estado.queue";
    public static final String QUEUE_CHAT_DLQ = "chat.dlq";

    public static final String ROUTING_KEY_PEDIDO_ESTADO = "pedido.estado.actualizado";
    public static final String ROUTING_KEY_CHAT_CREADO = "chat.creado";
    public static final String ROUTING_KEY_CHAT_MENSAJE_ENVIADO = "chat.mensaje.enviado";
    public static final String ROUTING_KEY_CHAT_CERRADO = "chat.cerrado";

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EXCHANGE_EVENTS, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(EXCHANGE_DLX, true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(QUEUE_CHAT_DLQ).build();
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()).with("chat.deadletter");
    }

    @Bean
    public Queue chatPedidoEstadoQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", EXCHANGE_DLX);
        args.put("x-dead-letter-routing-key", "chat.deadletter");
        return QueueBuilder.durable(QUEUE_CHAT_PEDIDOS_ESTADO).withArguments(args).build();
    }

    @Bean
    public Binding chatPedidoEstadoBinding() {
        return BindingBuilder.bind(chatPedidoEstadoQueue()).to(eventsExchange()).with(ROUTING_KEY_PEDIDO_ESTADO);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}
