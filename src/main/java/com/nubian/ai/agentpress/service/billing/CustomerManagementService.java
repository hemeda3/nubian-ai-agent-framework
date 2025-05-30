package com.nubian.ai.agentpress.service.billing;

import java.util.concurrent.CompletableFuture;

/**
 * Service interface for customer management operations.
 */
public interface CustomerManagementService {
    
    /**
     * Get the Stripe customer ID for a user.
     * 
     * @param userId The user ID
     * @return A CompletableFuture with the customer ID
     */
    CompletableFuture<String> getStripeCustomerId(String userId);
    
    /**
     * Create a new Stripe customer for a user.
     * 
     * @param userId The user ID
     * @param email The user's email
     * @return A CompletableFuture with the new customer ID
     */
    CompletableFuture<String> createStripeCustomer(String userId, String email);
    
    /**
     * Update a customer's active status.
     * 
     * @param customerId The customer ID
     * @param active Whether the customer is active
     * @return A CompletableFuture with the operation result
     */
    CompletableFuture<Void> updateCustomerStatus(String customerId, boolean active);
}
