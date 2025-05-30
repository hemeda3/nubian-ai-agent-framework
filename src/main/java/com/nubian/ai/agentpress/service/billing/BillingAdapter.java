package com.nubian.ai.agentpress.service.billing;

import com.nubian.ai.agentpress.model.billing.CheckoutSessionRequest;
import com.nubian.ai.agentpress.model.billing.PortalSessionRequest;
import com.nubian.ai.agentpress.model.billing.SubscriptionStatus;
import com.nubian.ai.agentpress.service.BillingService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Primary implementation of the BillingService interface.
 * This adapter delegates to the BillingServiceFacade and serves
 * as the main entry point for all billing operations.
 */
@Service
@Primary
public class BillingAdapter implements BillingService {
    
    private final BillingServiceFacade billingService;
    private final PricingService pricingService;
    
    public BillingAdapter(
        BillingServiceFacade billingService,
        PricingService pricingService
    ) {
        this.billingService = billingService;
        this.pricingService = pricingService;
    }
    
    @Override
    public CompletableFuture<Map<String, Object>> checkModelAccess(String userId, String modelName) {
        return billingService.canUseModel(userId, modelName);
    }
    
    @Override
    public CompletableFuture<Set<String>> getAccessibleModels(String userId) {
        return billingService.getAllowedModelsForUser(userId);
    }
    
    @Override
    public CompletableFuture<Map<String, Object>> getModelDetails(String userId) {
        return billingService.getAvailableModels(userId);
    }
    
    @Override
    public CompletableFuture<Map<String, Object>> checkBillingStatus(String userId) {
        return billingService.checkBillingStatus(userId);
    }
    
    @Override
    public CompletableFuture<SubscriptionStatus> getSubscriptionStatus(String userId) {
        return billingService.getSubscription(userId);
    }
    
    @Override
    public CompletableFuture<Map<String, Object>> createCheckoutSession(
        CheckoutSessionRequest request, 
        String userId, 
        String email
    ) {
        return billingService.createCheckoutSession(request, userId, email);
    }
    
    @Override
    public CompletableFuture<String> createPortalSession(
        PortalSessionRequest request, 
        String userId
    ) {
        return billingService.createPortalSession(request, userId);
    }
    
    @Override
    public void recordUsage(
        String userId, 
        String runId, 
        Instant startTime, 
        Instant endTime, 
        String modelName
    ) {
        billingService.recordUsage(userId, runId, startTime, endTime, modelName);
        // Also trigger usage calculation to update metrics
        calculateMonthlyUsage(userId);
    }
    
    @Override
    public CompletableFuture<Double> calculateMonthlyUsage(String userId) {
        return billingService.calculateMonthlyUsage(userId);
    }
    
    /**
     * Get all available pricing tiers.
     * 
     * @return A CompletableFuture with the list of pricing tiers
     */
    public CompletableFuture<List<Map<String, Object>>> getAvailableTiers() {
        return pricingService.getAvailableTiers();
    }
    
    /**
     * Get detailed information about a specific pricing tier.
     * 
     * @param priceId The price ID
     * @return A CompletableFuture with the pricing tier details
     */
    public CompletableFuture<Map<String, Object>> getTierDetails(String priceId) {
        return pricingService.getTierDetails(priceId);
    }
    
    /**
     * Compare two pricing tiers.
     * 
     * @param currentPriceId The current price ID
     * @param newPriceId The new price ID to compare to
     * @return A CompletableFuture with the comparison result
     */
    public CompletableFuture<Map<String, Object>> compareTiers(String currentPriceId, String newPriceId) {
        return pricingService.compareTiers(currentPriceId, newPriceId);
    }
}
