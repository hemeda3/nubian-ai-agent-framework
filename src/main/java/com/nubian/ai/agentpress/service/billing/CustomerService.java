package com.nubian.ai.agentpress.service.billing;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for customer management operations.
 */
public interface CustomerService {
    
    /**
     * Get the Stripe customer ID for a user.
     * 
     * @param userId The user ID
     * @return A CompletableFuture with the customer ID
     */
    CompletableFuture<String> getCustomerId(String userId);
    
    /**
     * Check the billing status of a customer.
     * 
     * @param customerId The customer ID
     * @return A CompletableFuture with the status information
     */
    CompletableFuture<Map<String, Object>> checkStatus(String customerId);
}
