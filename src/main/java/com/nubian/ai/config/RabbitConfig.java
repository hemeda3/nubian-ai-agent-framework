package com.nubian.ai.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionNameStrategy;
import org.springframework.amqp.rabbit.connection.SimplePropertyValueConnectionNameStrategy;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.ConditionalRejectingErrorHandler;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.ErrorHandler;

import lombok.extern.slf4j.Slf4j;

/**
 * RabbitMQ configuration for the Nubian AI Java application.
 * 
 * This configuration sets up the RabbitMQ connection and related beans needed for
 * message queuing, which is used to distribute agent run tasks across worker instances.
 * Includes connection retry and error handling mechanisms.
 * 
 * The configuration is designed to be fault-tolerant, allowing the application to
 * start up even if RabbitMQ is unavailable, and to automatically reconnect when
 * RabbitMQ becomes available.
 */
@Configuration
@Slf4j
public class RabbitConfig {
    
    @Autowired
    private ErrorHandler errorHandler;

    @Value("${spring.rabbitmq.host:localhost}")
    private String rabbitHost;

    @Value("${spring.rabbitmq.port:5672}")
    private int rabbitPort;

    @Value("${spring.rabbitmq.username:guest}")
    private String rabbitUsername;

    @Value("${spring.rabbitmq.password:guest}")
    private String rabbitPassword;
    
    @Value("${spring.application.name:Nubian-ai-java}")
    private String applicationName;
    
    @Value("${spring.rabbitmq.connection.retry.initial-interval:1000}")
    private long initialInterval;
    
    @Value("${spring.rabbitmq.connection.retry.max-interval:30000}")
    private long maxInterval;
    
    @Value("${spring.rabbitmq.connection.retry.multiplier:2.0}")
    private double multiplier;
    
    @Value("${spring.rabbitmq.connection.retry.max-attempts:5}")
    private int maxAttempts;

    /**
     * Creates a RabbitMQ connection factory with retry capabilities.
     * 
     * @return The RabbitMQ connection factory
     */
    @Bean
    public ConnectionFactory connectionFactory() {
        log.info("Configuring RabbitMQ connection to {}:{}", rabbitHost, rabbitPort);
        
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory();
        connectionFactory.setHost(rabbitHost);
        connectionFactory.setPort(rabbitPort);
        connectionFactory.setUsername(rabbitUsername);
        connectionFactory.setPassword(rabbitPassword);
        
        // Set connection name for better tracking in RabbitMQ management console
        connectionFactory.setConnectionNameStrategy(connectionNameStrategy());
        
        // Enable publisher returns and confirmations for more reliable messaging
        connectionFactory.setPublisherReturns(true);
        connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        
        return connectionFactory;
    }
    
    @Bean
    public ConnectionNameStrategy connectionNameStrategy() {
        return new SimplePropertyValueConnectionNameStrategy(applicationName + "-" + System.currentTimeMillis());
    }
    
    /**
     * Creates a retry template for RabbitMQ operations.
     * 
     * @return The retry template
     */
    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();
        
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(initialInterval);
        backOffPolicy.setMaxInterval(maxInterval);
        backOffPolicy.setMultiplier(multiplier);
        retryTemplate.setBackOffPolicy(backOffPolicy);
        
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(maxAttempts);
        retryTemplate.setRetryPolicy(retryPolicy);
        
        return retryTemplate;
    }

    /**
     * Creates a RabbitMQ template for sending messages with retry capabilities.
     * 
     * @param connectionFactory The RabbitMQ connection factory
     * @return The RabbitMQ template
     */
    @Bean
    @Primary
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        template.setRetryTemplate(retryTemplate());
        
        // Configure for more reliable messaging
        template.setMandatory(true);
        
        // Add logging for failed message delivery
        template.setReturnsCallback(returned -> {
            log.warn("Message returned from RabbitMQ: exchange={}, routingKey={}, replyCode={}, replyText={}",
                    returned.getExchange(), returned.getRoutingKey(), 
                    returned.getReplyCode(), returned.getReplyText());
        });
        
        return template;
    }
    
    /**
     * Creates a RabbitMQ listener container factory with retry capabilities and error handling.
     * 
     * @param connectionFactory The RabbitMQ connection factory
     * @return The RabbitMQ listener container factory
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter());
        factory.setErrorHandler(errorHandler);
        factory.setDefaultRequeueRejected(false);
        factory.setMissingQueuesFatal(false);
        
        // Set prefetch count (how many messages to fetch at once)
        factory.setPrefetchCount(1);
        
        return factory;
    }

    /**
     * Creates a RabbitMQ admin for queue management with retry capabilities.
     * 
     * @param connectionFactory The RabbitMQ connection factory
     * @return The RabbitMQ admin
     */
    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        admin.setRetryTemplate(retryTemplate());
        admin.setIgnoreDeclarationExceptions(true);
        return admin;
    }
    
    /**
     * Message recoverer for handling failed message processing
     */
    @Bean
    public MessageRecoverer messageRecoverer(RabbitTemplate rabbitTemplate) {
        return new RepublishMessageRecoverer(rabbitTemplate, "error.exchange", "error.routing.key");
    }

    /**
     * Creates a JSON message converter for RabbitMQ.
     * 
     * @return The JSON message converter
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * Creates the agent run queue.
     * 
     * @return The agent run queue
     */
    @Bean
    public Queue agentRunQueue() {
        return new Queue("agent-run-queue", true, false, false);
    }
    
    /**
     * Creates the error exchange for handling failed messages.
     * 
     * @return The error exchange
     */
    @Bean
    public org.springframework.amqp.core.Exchange errorExchange() {
        return new org.springframework.amqp.core.DirectExchange("error.exchange");
    }
    
    /**
     * Creates the error queue for handling failed messages.
     * 
     * @return The error queue
     */
    @Bean
    public Queue errorQueue() {
        return new Queue("error.queue", true);
    }
    
    /**
     * Creates a binding between the error exchange and error queue.
     * 
     * @return The binding
     */
    @Bean
    public org.springframework.amqp.core.Binding errorBinding() {
        return new org.springframework.amqp.core.Binding("error.queue", 
                org.springframework.amqp.core.Binding.DestinationType.QUEUE,
                "error.exchange", "error.routing.key", null);
    }
}
