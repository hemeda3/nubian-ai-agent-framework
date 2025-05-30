package com.nubian.ai.agentpress.service.impl;

import com.nubian.ai.config.BillingConfig;
import com.nubian.ai.agentpress.model.billing.CheckoutSessionRequest;
import com.nubian.ai.agentpress.model.billing.PortalSessionRequest;
import com.nubian.ai.agentpress.model.billing.SubscriptionStatus;
import com.nubian.ai.agentpress.service.BillingService;
import com.nubian.ai.agentpress.service.DBConnection;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration; 
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.List;

/**
 * Simple implementation of the Stripe billing service.
 * Used for environments when the real Stripe SDK is not available or integrated.
 */
@Service
@Profile({"default", "prod"}) // Activate this service in all environments for now
@Slf4j
public class SimpleBillingService implements BillingService {

    private final DBConnection dbConnection;
    private final BillingConfig billingConfig;

    @Autowired
    public SimpleBillingService(DBConnection dbConnection, BillingConfig billingConfig) {
        this.dbConnection = dbConnection;
        this.billingConfig = billingConfig;
        log.info("SimpleBillingService initialized - Basic billing implementation without Stripe");
    }

    @Override
    public CompletableFuture<Map<String, Object>> checkModelAccess(String userId, String modelName) {
        return getAccessibleModels(userId)
                .thenApply(allowedModels -> {
                    String resolvedModel = billingConfig.getCanonicalModelName(modelName);
                    Map<String, Object> result = new HashMap<>();
                    result.put("modelList", allowedModels);

                    if (allowedModels.contains(resolvedModel)) {
                        result.put("allowed", true);
                        result.put("message", "Model access allowed");
                    } else {
                        result.put("allowed", false);
                        result.put("message", "Your current subscription plan does not include access to " +
                                modelName + ". Please upgrade your subscription or choose from your available models: " +
                                String.join(", ", allowedModels));
                    }
                    return result;
                });
    }

    @Override
    public CompletableFuture<Set<String>> getAccessibleModels(String userId) {
        // For the mock, simply return the free tier models
        String freeTierId = billingConfig.getStripeFreeTierId();
        return CompletableFuture.completedFuture(billingConfig.getModelAccessTiers().get(freeTierId));
    }

    @Override
    public CompletableFuture<Map<String, Object>> getModelDetails(String userId) {
        return getAccessibleModels(userId)
                .thenApply(allowedModels -> {
                    Map<String, String> modelAliases = billingConfig.getModelNameAliases();
                    List<Map<String, Object>> modelInfo = allowedModels.stream()
                            .map(model -> {
                                String displayName = model;
                                String shortName = null;
                                for (Map.Entry<String, String> entry : modelAliases.entrySet()) {
                                    if (entry.getValue().equals(model) && !entry.getKey().equals(model)) {
                                        shortName = entry.getKey();
                                        if (!shortName.contains("/")) {
                                            displayName = shortName;
                                        }
                                        break;
                                    }
                                }
                                Map<String, Object> info = new HashMap<>();
                                info.put("id", model);
                                info.put("display_name", displayName);
                                info.put("short_name", shortName);
                                return info;
                            })
                            .collect(Collectors.toList());

                    Map<String, Object> result = new HashMap<>();
                    result.put("models", modelInfo);
                    result.put("subscription_tier", "free");
                    result.put("total_models", modelInfo.size());
                    return result;
                });
    }

    @Override
    public CompletableFuture<Map<String, Object>> checkBillingStatus(String userId) {
        // For the mock, just return the free tier status
        return CompletableFuture.completedFuture(createFreeTierStatus());
    }

    private Map<String, Object> createFreeTierStatus() {
        Map<String, Object> subscriptionInfo = new HashMap<>();
        String freeTierId = billingConfig.getStripeFreeTierId();
        Map<String, Object> freeTierInfo = billingConfig.getSubscriptionTiers().get(freeTierId);

        subscriptionInfo.put("price_id", freeTierId);
        subscriptionInfo.put("plan_name", freeTierInfo.get("name"));
        subscriptionInfo.put("minutes_limit", freeTierInfo.get("minutes"));
        subscriptionInfo.put("status", "active"); // Free tier is always active
        
        Map<String, Object> result = new HashMap<>();
        result.put("subscription", subscriptionInfo);
        result.put("can_run", true); // Free tier always allows running
        result.put("message", "OK");
        return result;
    }

    @Override
    public CompletableFuture<SubscriptionStatus> getSubscriptionStatus(String userId) {
        // For the mock, just return the free tier subscription status
        return CompletableFuture.completedFuture(createFreeTierSubscriptionStatus());
    }

    private SubscriptionStatus createFreeTierSubscriptionStatus() {
        String freeTierId = billingConfig.getStripeFreeTierId();
        Map<String, Object> freeTierInfo = billingConfig.getSubscriptionTiers().get(freeTierId);
        SubscriptionStatus status = new SubscriptionStatus();
        status.setStatus("no_subscription");
        status.setPlanName((String) freeTierInfo.get("name"));
        status.setPriceId(freeTierId);
        status.setMinutesLimit((Integer) freeTierInfo.get("minutes"));
        status.setCurrentUsage(0.0);
        return status;
    }

    @Override
    public CompletableFuture<Map<String, Object>> createCheckoutSession(
            CheckoutSessionRequest request, String userId, String email) {
        // For the mock, just return a dummy success response
        Map<String, Object> response = new HashMap<>();
        response.put("session_id", UUID.randomUUID().toString());
        response.put("url", "https://example.com/checkout/" + UUID.randomUUID().toString());
        response.put("status", "new");
        return CompletableFuture.completedFuture(response);
    }

    @Override
    public CompletableFuture<String> createPortalSession(PortalSessionRequest request, String userId) {
        // For the mock, just return a dummy portal URL
        return CompletableFuture.completedFuture("https://example.com/customer-portal/" + UUID.randomUUID().toString());
    }

    @Override
    public void recordUsage(String userId, String runId, Instant startTime, Instant endTime, String modelName) {
        // For the mock, just log the usage and don't do anything else
        long durationSeconds = Duration.between(startTime, endTime).getSeconds();
        double usageMinutes = (double) durationSeconds / 60.0;
        log.info("Recording {} minutes of usage for user {} (run {}) on model {}", 
                usageMinutes, userId, runId, modelName);
        
        try {
            // Still persist to DB for reporting
            Map<String, Object> usageData = new HashMap<>();
            usageData.put("user_id", userId);
            usageData.put("run_id", runId);
            usageData.put("model_name", modelName);
            usageData.put("minutes", usageMinutes);
            usageData.put("created_at", LocalDateTime.now()); // Assuming DB handles timestamp conversion or column type is appropriate

            dbConnection.insert("usage_records", usageData, false).join(); // Using join to ensure completion for logging
            log.debug("Persisted usage record to database");
        } catch (Exception e) {
            log.error("Failed to persist usage record: {}", e.getMessage(), e);
        }
    }

    @Override
    public CompletableFuture<Double> calculateMonthlyUsage(String userId) {
        // Calculate usage from the start of the current billing period
        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        
        String sql = "SELECT COALESCE(SUM(minutes), 0) as total_minutes FROM usage_records WHERE user_id = ? AND created_at >= ?";
        return dbConnection.queryForList(sql, userId, startOfMonth.toString())
                .thenApply(results -> {
                    if (results.isEmpty() || results.get(0).get("total_minutes") == null) {
                        return 0.0;
                    }
                    return ((Number) results.get(0).get("total_minutes")).doubleValue();
                })
                .exceptionally(e -> {
                    log.error("Error calculating monthly usage: {}", e.getMessage(), e);
                    return 0.0;
                });
    }
}
