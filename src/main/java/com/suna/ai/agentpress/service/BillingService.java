package com.Nubian.ai.agentpress.service;

import com.Nubian.ai.agentpress.model.billing.CheckoutSessionRequest;
import com.Nubian.ai.agentpress.model.billing.PortalSessionRequest;
import com.Nubian.ai.agentpress.model.billing.SubscriptionStatus;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for billing operations.
 * Provides methods for subscription management, model access control,
 * and usage tracking.
 */
public interface BillingService {
    
    /**
     * Check if a user has access to a specific model.
     * 
     * @param userId The user ID
     * @param modelName The model name to check access for
     * @return A CompletableFuture with the access information
     */
    CompletableFuture<Map<String, Object>> checkModelAccess(String userId, String modelName);
    
    /**
     * Get the set of models allowed for a user.
     * 
     * @param userId The user ID
     * @return A CompletableFuture with the set of allowed model IDs
     */
    CompletableFuture<Set<String>> getAccessibleModels(String userId);
    
    /**
     * Get detailed information about available models for a user.
     * 
     * @param userId The user ID
     * @return A CompletableFuture with model details
     */
    CompletableFuture<Map<String, Object>> getModelDetails(String userId);
    
    /**
     * Check the billing status of a user.
     * 
     * @param userId The user ID
     * @return A CompletableFuture with the status information
     */
    CompletableFuture<Map<String, Object>> checkBillingStatus(String userId);
    
    /**
     * Get the current subscription status for a user.
     * 
     * @param userId The user ID
     * @return A CompletableFuture with the subscription status
     */
    CompletableFuture<SubscriptionStatus> getSubscriptionStatus(String userId);
    
    /**
     * Create a checkout session for subscription creation or modification.
     * 
     * @param request The checkout session request
     * @param userId The user ID
     * @param email The user's email
     * @return A CompletableFuture with the checkout session details
     */
    CompletableFuture<Map<String, Object>> createCheckoutSession(
        CheckoutSessionRequest request, 
        String userId, 
        String email
    );
    
    /**
     * Create a customer portal session for subscription management.
     * 
     * @param request The portal session request
     * @param userId The user ID
     * @return A CompletableFuture with the portal session URL
     */
    CompletableFuture<String> createPortalSession(
        PortalSessionRequest request, 
        String userId
    );
    
    /**
     * Record usage for a specific run.
     * 
     * @param userId The user ID
     * @param runId The run ID
     * @param startTime The start time of the run
     * @param endTime The end time of the run
     * @param modelName The model name used
     */
    void recordUsage(
        String userId, 
        String runId, 
        Instant startTime, 
        Instant endTime, 
        String modelName
    );
    
    /**
     * Calculate the total usage for the current month.
     * 
     * @param userId The user ID
     * @return A CompletableFuture with the total usage
     */
    CompletableFuture<Double> calculateMonthlyUsage(String userId);
}
