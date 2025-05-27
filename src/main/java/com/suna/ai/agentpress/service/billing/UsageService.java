package com.Nubian.ai.agentpress.service.billing;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for usage tracking and billing operations.
 */
public interface UsageService {
    
    /**
     * Calculate monthly usage for a user.
     * 
     * @param userId The user ID
     * @return A CompletableFuture with the total usage in minutes
     */
    CompletableFuture<Double> calculateMonthlyUsage(String userId);
    
    /**
     * Record usage for a specific run.
     * 
     * @param customerId The customer ID
     * @param runId The run ID
     * @param modelName The model name used
     * @param usage The usage amount (e.g., tokens or time)
     * @return A CompletableFuture with the operation result
     */
    CompletableFuture<Void> recordUsage(
        String customerId, 
        String runId, 
        String modelName, 
        Double usage
    );
    
    /**
     * Calculate the total usage for the current month.
     * 
     * @param customerId The customer ID
     * @return A CompletableFuture with the total usage
     */
    CompletableFuture<Double> getMonthlyUsage(String customerId);
    
    /**
     * Get historical usage data.
     * 
     * @param customerId The customer ID
     * @return A CompletableFuture with the usage history
     */
    CompletableFuture<List<Map<String, Object>>> getUsageHistory(String customerId);
}
