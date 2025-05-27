package com.Nubian.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.Nubian.ai.agent.config.AgentConfig;
import com.Nubian.ai.agentpress.config.AgentPressConfig;

import lombok.extern.slf4j.Slf4j;

/**
 * Main entry point for the Nubian AI Java application.
 * 
 * Note on Production Readiness:
 * - Thread Pools: For production, consider defining custom thread pools (e.g., using @EnableAsync and AsyncConfigurer)
 *   to manage concurrency for @Async and CompletableFuture operations, ensuring optimal performance and resource utilization.
 *   Proper error propagation across async boundaries should also be thoroughly reviewed.
 * - Configuration: While @EnableConfigurationProperties is used, further refactoring of individual @Value annotations
 *   into dedicated @ConfigurationProperties classes can improve type safety and maintainability.
 * 
 * This application implements a Java version of the Nubian AI platform,
 * which consists of two main components:
 * 
 * 1. AgentPress - The core framework for LLM-based tools and context management
 * 2. Agent - Specific agent implementations that utilize the AgentPress framework
 */
@SpringBootApplication
@EnableConfigurationProperties({AgentPressConfig.class, AgentConfig.class})
@Slf4j
public class NubianAiJavaApplication {

    public static void main(String[] args) {
        SpringApplication.run(NubianAiJavaApplication.class, args);
        log.info("Nubian AI Java application started successfully");
    }
}
