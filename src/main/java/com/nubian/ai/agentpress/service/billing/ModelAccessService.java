package com.nubian.ai.agentpress.service.billing;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for model access management operations.
 */
public interface ModelAccessService {
    
    /**
     * Check if a customer has access to a specific model.
     * 
     * @param customerId The customer ID
     * @param modelName The model name to check access for
     * @return A CompletableFuture with the access information
     */
    CompletableFuture<Map<String, Object>> checkModelAccess(String customerId, String modelName);
    
    /**
     * Get the set of models allowed for a customer.
     * 
     * @param customerId The customer ID
     * @return A CompletableFuture with the set of allowed model IDs
     */
    CompletableFuture<Set<String>> getAllowedModels(String customerId);
    
    /**
     * Get detailed information about available models for a customer.
     * 
     * @param customerId The customer ID
     * @return A CompletableFuture with model details
     */
    CompletableFuture<Map<String, Object>> getAvailableModels(String customerId);
}
