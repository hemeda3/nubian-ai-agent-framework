package com.nubian.ai.config;

import org.springframework.amqp.rabbit.listener.ConditionalRejectingErrorHandler;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ErrorHandler;

import lombok.extern.slf4j.Slf4j;

/**
 * Error handler configuration for RabbitMQ messaging.
 * 
 * This class provides a custom error handler for RabbitMQ message listeners
 * that improves error logging and handling for failed message processing.
 */
@Configuration
@Slf4j
public class RabbitErrorHandler {

    /**
     * Creates a custom error handler for RabbitMQ message listeners.
     * 
     * @return The custom error handler
     */
    @Bean
    public ErrorHandler errorHandler() {
        return new ConditionalRejectingErrorHandler(new CustomExceptionStrategy());
    }
    
    /**
     * Custom exception strategy that determines which exceptions should cause message rejection.
     */
    private static class CustomExceptionStrategy extends ConditionalRejectingErrorHandler.DefaultExceptionStrategy {
        @Override
        public boolean isFatal(Throwable t) {
            if (t instanceof ListenerExecutionFailedException) {
                ListenerExecutionFailedException leef = (ListenerExecutionFailedException) t;
                log.error("Failed to process inbound message from queue: {}", 
                          leef.getFailedMessage() != null ? leef.getFailedMessage().getMessageProperties().getConsumerQueue() : "unknown",
                          t);
                return true;
            }
            return super.isFatal(t);
        }
    }
}
