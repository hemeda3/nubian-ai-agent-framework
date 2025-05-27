package com.Nubian.ai.agentpress.service.billing.impl;

import com.Nubian.ai.agentpress.service.billing.PricingService;
import com.Nubian.ai.config.BillingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import com.Nubian.ai.agentpress.service.DBConnection;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of PricingService for managing pricing tiers.
 */
@Service
public class PricingServiceImpl implements PricingService {

    private static final Logger logger = LoggerFactory.getLogger(PricingServiceImpl.class);
    private final DBConnection dbConnection;
    private final BillingConfig billingConfig;

    @Autowired
    public PricingServiceImpl(DBConnection dbConnection, BillingConfig billingConfig) {
        this.dbConnection = dbConnection;
        this.billingConfig = billingConfig;
    }

    @Override
    public CompletableFuture<List<Map<String, Object>>> getAvailableTiers() {
        logger.debug("Getting available pricing tiers");
        return CompletableFuture.supplyAsync(() -> {
            List<Map<String, Object>> tiers = new ArrayList<>();
            
            // Add tiers from billing config
            Map<String, Map<String, Object>> subscriptionTiers = billingConfig.getSubscriptionTiers();
            for (Map.Entry<String, Map<String, Object>> entry : subscriptionTiers.entrySet()) {
                String priceId = entry.getKey();
                Map<String, Object> tierDetails = entry.getValue();
                
                Map<String, Object> tier = new HashMap<>(tierDetails);
                tier.put("id", priceId);
                tiers.add(tier);
            }
            
            return tiers;
        });
    }

    @Override
    public CompletableFuture<Map<String, Object>> getTierDetails(String priceId) {
        logger.debug("Getting details for pricing tier: {}", priceId);
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Map<String, Object>> subscriptionTiers = billingConfig.getSubscriptionTiers();
            Map<String, Object> tierDetails = subscriptionTiers.get(priceId);
            
            if (tierDetails == null) {
                logger.warn("No details found for pricing tier: {}", priceId);
                return new HashMap<>();
            }
            
            Map<String, Object> details = new HashMap<>(tierDetails);
            details.put("id", priceId);
            
            // Add model access information
            Map<String, Set<String>> modelAccessTiers = billingConfig.getModelAccessTiers();
            Set<String> allowedModels = modelAccessTiers.get(priceId);
            if (allowedModels != null) {
                details.put("allowed_models", allowedModels);
            }
            
            return details;
        });
    }

    @Override
    public CompletableFuture<Map<String, Object>> compareTiers(String currentPriceId, String newPriceId) {
        logger.debug("Comparing pricing tiers: {} vs {}", currentPriceId, newPriceId);
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Object> comparison = new HashMap<>();
            
            // Get details for both tiers
            Map<String, Map<String, Object>> subscriptionTiers = billingConfig.getSubscriptionTiers();
            Map<String, Object> currentTier = subscriptionTiers.get(currentPriceId);
            Map<String, Object> newTier = subscriptionTiers.get(newPriceId);
            
            if (currentTier == null || newTier == null) {
                logger.warn("One or both pricing tiers not found: {} or {}", currentPriceId, newPriceId);
                comparison.put("error", "One or both pricing tiers not found");
                return comparison;
            }
            
            // Compare minutes
            Integer currentMinutes = (Integer) currentTier.get("minutes");
            Integer newMinutes = (Integer) newTier.get("minutes");
            int minutesDiff = newMinutes - currentMinutes;
            
            // Compare price
            Double currentPrice = (Double) currentTier.get("price_usd");
            Double newPrice = (Double) newTier.get("price_usd");
            double priceDiff = newPrice - currentPrice;
            
            // Compare model access
            Map<String, Set<String>> modelAccessTiers = billingConfig.getModelAccessTiers();
            Set<String> currentModels = modelAccessTiers.get(currentPriceId);
            Set<String> newModels = modelAccessTiers.get(newPriceId);
            
            Set<String> additionalModels = new HashSet<>(newModels);
            additionalModels.removeAll(currentModels);
            
            // Build comparison result
            comparison.put("current_tier", currentTier.get("name"));
            comparison.put("new_tier", newTier.get("name"));
            comparison.put("minutes_difference", minutesDiff);
            comparison.put("price_difference", priceDiff);
            comparison.put("additional_models", additionalModels);
            comparison.put("is_upgrade", newPrice > currentPrice);
            
            return comparison;
        });
    }
}
