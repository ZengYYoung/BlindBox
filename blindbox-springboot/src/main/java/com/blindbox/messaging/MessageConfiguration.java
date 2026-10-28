package com.blindbox.messaging;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name="app.messaging.enabled", havingValue="true")
public class MessageConfiguration {
    public static final String EXCHANGE="blindbox.draw.v1";
    public static final String QUEUE="blindbox.draw.notification.v1";
    public static final String DEAD_EXCHANGE="blindbox.draw.dead.v1";
    public static final String DEAD_QUEUE="blindbox.draw.notification.dead.v1";
    public static final String KEY="draw.created";

    @Bean DirectExchange drawExchange() { return new DirectExchange(EXCHANGE,true,false); }
    @Bean DirectExchange drawDeadExchange() { return new DirectExchange(DEAD_EXCHANGE,true,false); }
    @Bean Queue notificationQueue() {
        return QueueBuilder.durable(QUEUE).deadLetterExchange(DEAD_EXCHANGE).deadLetterRoutingKey(KEY).build();
    }
    @Bean Queue notificationDeadQueue() { return QueueBuilder.durable(DEAD_QUEUE).build(); }
    @Bean Binding drawBinding() { return BindingBuilder.bind(notificationQueue()).to(drawExchange()).with(KEY); }
    @Bean Binding deadBinding() { return BindingBuilder.bind(notificationDeadQueue()).to(drawDeadExchange()).with(KEY); }
    @Bean RabbitTemplate drawRabbitTemplate(ConnectionFactory factory) {
        RabbitTemplate template=new RabbitTemplate(factory);
        template.setMandatory(true);
        return template;
    }
    @Bean SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            SimpleRabbitListenerContainerFactoryConfigurer configurer, ConnectionFactory factory) {
        SimpleRabbitListenerContainerFactory container=new SimpleRabbitListenerContainerFactory();
        configurer.configure(container,factory);
        container.setAcknowledgeMode(AcknowledgeMode.AUTO);
        container.setDefaultRequeueRejected(false);
        container.setAdviceChain(RetryInterceptorBuilder.stateless().maxAttempts(3)
            .backOffOptions(1000,2.0,5000).recoverer(new RejectAndDontRequeueRecoverer()).build());
        container.setPrefetchCount(10);
        return container;
    }
}
