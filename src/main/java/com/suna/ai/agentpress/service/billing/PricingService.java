package com.Nubian.ai.agentpress.service.billing;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service interface for pricing tier operations.
 */
public interface PricingService {
    
    /**
     * Get all available pricing tiers.
     * 
     * @return A CompletableFuture with the list of pricing tiers
     */
    CompletableFuture<List<Map<String, Object>>> getAvailableTiers();
    
    /**
     * Get detailed information about a specific pricing tier.
     * 
     * @param priceId The price ID
     * @return A CompletableFuture with the pricing tier details
     */
    CompletableFuture<Map<String, Object>> getTierDetails(String priceId);
    
    /**
     * Compare two pricing tiers.
     * 
     * @param currentPriceId The current price ID
     * @param newPriceId The new price ID to compare to
     * @return A CompletableFuture with the comparison result
     */
    CompletableFuture<Map<String, Object>> compareTiers(String currentPriceId, String newPriceId);
}
