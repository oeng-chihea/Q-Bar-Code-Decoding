package com.excel.reconciler.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;

@Configuration
public class RabbitMqConfig {
    public static final String EXCHANGE = "barcode.reconciliation.exchange";
    public static final String ROUTING_KEY = "barcode.reconciliation.requested";
    public static final String QUEUE = "barcode.reconciliation.queue";
    public static final String DEAD_LETTER_EXCHANGE = "barcode.reconciliation.dlx";
    public static final String DEAD_LETTER_ROUTING_KEY = "barcode.reconciliation.failed";
    public static final String DEAD_LETTER_QUEUE = "barcode.reconciliation.failed";

    @Bean
    public DirectExchange reconciliationExchange() {
        return new DirectExchange(EXCHANGE);
    }

    @Bean
    public DirectExchange reconciliationDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Queue reconciliationQueue() {
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue reconciliationDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding reconciliationQueueBinding(@Qualifier("reconciliationQueue") Queue reconciliationQueue,
                                              @Qualifier("reconciliationExchange") DirectExchange reconciliationExchange) {
        return BindingBuilder.bind(reconciliationQueue)
                .to(reconciliationExchange)
                .with(ROUTING_KEY);
    }

    @Bean
    public Binding reconciliationDeadLetterBinding(@Qualifier("reconciliationDeadLetterQueue") Queue reconciliationDeadLetterQueue,
                                                   @Qualifier("reconciliationDeadLetterExchange") DirectExchange reconciliationDeadLetterExchange) {
        return BindingBuilder.bind(reconciliationDeadLetterQueue)
                .to(reconciliationDeadLetterExchange)
                .with(DEAD_LETTER_ROUTING_KEY);
    }

    @Bean
    public MessageConverter reconciliationMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean(name = "rabbitListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter reconciliationMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(reconciliationMessageConverter);
        factory.setPrefetchCount(1);
        factory.setDefaultRequeueRejected(false);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(1000, 2.0, 10000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build());
        return factory;
    }
}
