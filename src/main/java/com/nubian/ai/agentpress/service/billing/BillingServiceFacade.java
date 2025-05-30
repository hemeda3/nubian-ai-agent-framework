package com.nubian.ai.agentpress.service.billing;

import com.nubian.ai.agentpress.model.billing.CheckoutSessionRequest;
import com.nubian.ai.agentpress.model.billing.PortalSessionRequest;
import com.nubian.ai.agentpress.model.billing.SubscriptionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Facade service that coordinates various billing-related operations.
 * Acts as a single entry point for all billing functionality by delegating
 * to specialized services.
 */
public class BillingServiceFacade {
    
    private final SubscriptionService subscriptionService;
    private final CustomerService customerService;
    private final UsageService usageService;
    private final ModelAccessService modelAccessService;
    
    public BillingServiceFacade(
        SubscriptionService subscriptionService,
        CustomerService customerService,
        UsageService usageService,
        ModelAccessService modelAccessService
    ) {
        this.subscriptionService = subscriptionService;
        this.customerService = customerService;
        this.usageService = usageService;
        this.modelAccessService = modelAccessService;
    }
    
    /**
     * Check if a user has access to a specific model.
     * 
     * @param userId The user ID
     * @param modelName The model name to check access for
     * @return A CompletableFuture with the access information
     */
    public CompletableFuture<Map<String, Object>> canUseModel(String userId, String modelName) {
        return customerService.getCustomerId(userId)
            .thenCompose(customerId -> modelAccessService.checkModelAccess(customerId, modelName));
    }
    
    /**
     * Get the set of models allowed for a user.
     * 
     * @param userId The user ID
     * @return A CompletableFuture with the set of allowed model IDs
     */
    public CompletableFuture<Set<String>> getAllowedModelsForUser(String userId) {
        return customerService.getCustomerId(userId)
            .thenCompose(modelAccessService::getAllowedModels);
    }
    
    /**
     * Get detailed information about available models for a user.
     * 
     * @param userId The user ID
     * @return A CompletableFuture with model details
     */
    public CompletableFuture<Map<String, Object>> getAvailableModels(String userId) {
        return customerService.getCustomerId(userId)
            .thenCompose(modelAccessService::getAvailableModels);
    }
    
    /**
     * Check the billing status of a user.
     * 
     * @param userId The user ID
     * @return A CompletableFuture with the status information
     */
    public CompletableFuture<Map<String, Object>> checkBillingStatus(String userId) {
        return customerService.getCustomerId(userId)
            .thenCompose(customerService::checkStatus);
    }
    
    /**
     * Get the current subscription status for a user.
     * 
     * @param userId The user ID
     * @return A CompletableFuture with the subscription status
     */
    public CompletableFuture<SubscriptionStatus> getSubscription(String userId) {
        return customerService.getCustomerId(userId)
            .thenCompose(subscriptionService::getSubscriptionStatus);
    }
    
    /**
     * Create a checkout session for subscription creation or modification.
     * 
     * @param request The checkout session request
     * @param userId The user ID
     * @param email The user's email
     * @return A CompletableFuture with the checkout session details
     */
    public CompletableFuture<Map<String, Object>> createCheckoutSession(
        CheckoutSessionRequest request, 
        String userId, 
        String email
    ) {
        return customerService.getCustomerId(userId)
            .thenCompose(customerId -> 
                subscriptionService.createCheckoutSession(request, customerId, email));
    }
    
    /**
     * Create a customer portal session for subscription management.
     * 
     * @param request The portal session request
     * @param userId The user ID
     * @return A CompletableFuture with the portal session URL
     */
    public CompletableFuture<String> createPortalSession(
        PortalSessionRequest request, 
        String userId
    ) {
        return customerService.getCustomerId(userId)
            .thenCompose(customerId -> 
                subscriptionService.createPortalSession(request, customerId));
    }
    
    /**
     * Record usage for a specific run.
     * 
     * @param userId The user ID
     * @param runId The run ID
     * @param startTime The start time of the run
     * @param endTime The end time of the run
     * @param modelName The model name used
     */
    public void recordUsage(
        String userId, 
        String runId, 
        Instant startTime, 
        Instant endTime, 
        String modelName
    ) {
        // Calculate usage based on time difference
        double timeInSeconds = (endTime.toEpochMilli() - startTime.toEpochMilli()) / 1000.0;
        double usage = Math.max(timeInSeconds, 1.0); // Minimum 1 second
        
        customerService.getCustomerId(userId)
            .thenCompose(customerId -> 
                usageService.recordUsage(customerId, runId, modelName, usage));
        
        // Also trigger usage calculation
        calculateMonthlyUsage(userId);
    }
    
    /**
     * Record usage for a specific run based on tokens.
     * 
     * @param userId The user ID
     * @param runId The run ID
     * @param startTime The start time of the run
     * @param endTime The end time of the run
     * @param modelName The model name used
     * @param promptTokens The number of prompt tokens used
     * @param completionTokens The number of completion tokens used
     */
    public void recordUsage(
        String userId, 
        String runId, 
        Instant startTime, 
        Instant endTime, 
        String modelName,
        long promptTokens,
        long completionTokens
    ) {
        // Calculate usage based on tokens
        double tokenUsage = promptTokens + completionTokens;
        
        customerService.getCustomerId(userId)
            .thenCompose(customerId -> 
                usageService.recordUsage(customerId, runId, modelName, tokenUsage));
        
        // Also trigger usage calculation
        calculateMonthlyUsage(userId);
    }
    
    /**
     * Calculate the total usage for the current month.
     * 
     * @param userId The user ID
     * @return A CompletableFuture with the total usage
     */
    public CompletableFuture<Double> calculateMonthlyUsage(String userId) {
        return customerService.getCustomerId(userId)
            .thenCompose(usageService::getMonthlyUsage);
    }
    
    /**
     * Get historical usage data.
     * 
     * @param userId The user ID
     * @return A CompletableFuture with the usage history
     */
    public CompletableFuture<List<Map<String, Object>>> getUsageHistory(String userId) {
        return customerService.getCustomerId(userId)
            .thenCompose(usageService::getUsageHistory);
    }
}
