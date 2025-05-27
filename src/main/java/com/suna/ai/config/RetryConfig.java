package com.Nubian.ai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

/**
 * Configuration class to enable Spring Retry functionality.
 * 
 * This configuration enables the use of @Retryable and @Recover annotations
 * for declarative retry logic throughout the application, particularly
 * useful for handling transient failures in external service calls.
 */
@Configuration
@EnableRetry
public class RetryConfig {
    // No additional beans needed - @EnableRetry handles the retry aspects
}
