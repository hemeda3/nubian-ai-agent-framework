package com.Nubian.ai.agentpress.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.Nubian.ai.agentpress.service.billing.*;
import com.Nubian.ai.agentpress.service.billing.impl.CustomerServiceImpl;
import com.Nubian.ai.agentpress.service.billing.impl.PricingServiceImpl;
import com.Nubian.ai.agentpress.service.billing.impl.SubscriptionServiceImpl;
import com.Nubian.ai.config.BillingConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Configuration for billing services.
 * This configuration sets up the beans needed for the billing system.
 */
@Configuration
public class BillingServiceConfig {

    @Autowired
    private BillingConfig billingConfig;

    @Autowired
    private com.Nubian.ai.agentpress.service.DBConnection dbConnection;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Create a CustomerService bean.
     *
     * @return The CustomerService
     */
    @Bean
    public CustomerService customerService() {
        return new com.Nubian.ai.agentpress.service.billing.impl.CustomerServiceImpl(dbConnection, objectMapper);
    }

    /**
     * Create a SubscriptionService bean.
     *
     * @return The SubscriptionService
     */
    @Bean
    public SubscriptionService subscriptionService() {
        return new com.Nubian.ai.agentpress.service.billing.impl.SubscriptionServiceImpl(dbConnection, objectMapper, billingConfig);
    }

    /**
     * Create a UsageService bean.
     *
     * @return The UsageService
     */
    @Bean
    public UsageService usageService() {
        // Create a simplified implementation
        return new UsageService() {
            @Override
            public CompletableFuture<Void> recordUsage(String customerId, String runId, String modelName, Double usage) {
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletableFuture<Double> calculateMonthlyUsage(String userId) {
                return CompletableFuture.completedFuture(0.0);
            }

            @Override
            public CompletableFuture<Double> getMonthlyUsage(String customerId) {
                return CompletableFuture.completedFuture(0.0);
            }

            @Override
            public CompletableFuture<List<Map<String, Object>>> getUsageHistory(String customerId) {
                return CompletableFuture.completedFuture(new ArrayList<>());
            }
        };
    }

    /**
     * Create a ModelAccessService bean.
     *
     * @return The ModelAccessService
     */
    @Bean
    public ModelAccessService modelAccessService() {
        // Create a simplified implementation
        return new ModelAccessService() {
            @Override
            public CompletableFuture<Map<String, Object>> checkModelAccess(String customerId, String modelName) {
                Map<String, Object> result = new HashMap<>();
                result.put("allowed", true);
                return CompletableFuture.completedFuture(result);
            }

            @Override
            public CompletableFuture<Set<String>> getAllowedModels(String customerId) {
                return CompletableFuture.completedFuture(new HashSet<>());
            }

            @Override
            public CompletableFuture<Map<String, Object>> getAvailableModels(String customerId) {
                return CompletableFuture.completedFuture(new HashMap<>());
            }
        };
    }
    
    /**
     * Create a PricingService bean.
     *
     * @return The PricingService
     */
    @Bean
    public PricingService pricingService() {
        return new PricingServiceImpl(dbConnection, billingConfig);
    }

    /**
     * Create a BillingServiceFacade bean.
     *
     * @param subscriptionService The SubscriptionService
     * @param customerService The CustomerService
     * @param usageService The UsageService
     * @param modelAccessService The ModelAccessService
     * @return The BillingServiceFacade
     */
    @Bean
    public BillingServiceFacade billingServiceFacade(
            SubscriptionService subscriptionService,
            CustomerService customerService,
            UsageService usageService,
            ModelAccessService modelAccessService) {
        return new BillingServiceFacade(
                subscriptionService,
                customerService,
                usageService,
                modelAccessService);
    }
}
