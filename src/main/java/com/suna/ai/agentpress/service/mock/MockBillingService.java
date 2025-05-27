package com.Nubian.ai.agentpress.service.mock;

import com.Nubian.ai.config.BillingConfig;
import com.Nubian.ai.agentpress.model.billing.CheckoutSessionRequest;
import com.Nubian.ai.agentpress.model.billing.PortalSessionRequest;
import com.Nubian.ai.agentpress.model.billing.SubscriptionStatus;
import com.Nubian.ai.agentpress.service.DBConnection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Mock implementation of a Stripe billing service.
 * Used for development and testing purposes without actual Stripe integration.
 * 
 * TODO: Replace this mock implementation with a real integration using the Stripe Java SDK
 * for production environments. This involves implementing all methods to interact with the
 * Stripe API for customer management, subscriptions, usage reporting, webhooks, etc.
 */
@Service
@Profile("dev")
public class MockBillingService {
    private static final Logger logger = LoggerFactory.getLogger(MockBillingService.class);
    
    // In-memory storage for mock data
    private final Map<String, String> customerIds = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> subscriptions = new ConcurrentHashMap<>();
    private final Map<String, Double> usageMinutes = new ConcurrentHashMap<>();
    
    private final DBConnection dbConnection;
    private final BillingConfig billingConfig;
    
    @Autowired
    public MockBillingService(DBConnection dbConnection, BillingConfig billingConfig) {
        this.dbConnection = dbConnection;
        this.billingConfig = billingConfig;
        logger.info("MockBillingService initialized (Development Mode)");
    }
    
    /**
     * Get the Stripe customer ID for a user.
     *
     * @param userId The user ID
     * @return CompletableFuture that completes with the customer ID or null if not found
     */
    public CompletableFuture<String> getStripeCustomerId(String userId) {
        return CompletableFuture.supplyAsync(() -> customerIds.get(userId));
    }
    
    /**
     * Create a new Stripe customer for a user.
     *
     * @param userId The user ID
     * @param email The user's email
     * @return CompletableFuture that completes with the new customer ID
     */
    public CompletableFuture<String> createStripeCustomer(String userId, String email) {
        return CompletableFuture.supplyAsync(() -> {
            String customerId = "cus_mock_" + UUID.randomUUID().toString().substring(0, 8);
            customerIds.put(userId, customerId);
            
            // Mock database insert
            logger.info("Mock: Created Stripe customer {} for user {} with email {}", customerId, userId, email);
            
            return customerId;
        });
    }
    
    /**
     * Get the current subscription for a user.
     *
     * @param userId The user ID
     * @return CompletableFuture that completes with the subscription or null if none
     */
    public CompletableFuture<Map<String, Object>> getUserSubscription(String userId) {
        return getStripeCustomerId(userId)
            .thenApply(customerId -> {
                if (customerId == null) {
                    return null;
                }
                
                return subscriptions.get(customerId);
            });
    }
    
    /**
     * Calculate the total usage minutes for a user in the current month.
     *
     * @param userId The user ID
     * @return CompletableFuture that completes with the total minutes
     */
    public CompletableFuture<Double> calculateMonthlyUsage(String userId) {
        return CompletableFuture.supplyAsync(() -> usageMinutes.getOrDefault(userId, 0.0));
    }
    
    /**
     * Get the list of models allowed for a user based on their subscription tier.
     *
     * @param userId The user ID
     * @return CompletableFuture that completes with the list of allowed model names
     */
    public CompletableFuture<Set<String>> getAllowedModelsForUser(String userId) {
        return getUserSubscription(userId)
            .thenApply(subscription -> {
                // Default to free tier
                String tierId = billingConfig.getStripeFreeTierId();
                
                if (subscription != null) {
                    // Get the current price ID from the subscription
                    String priceId = (String) subscription.get("price_id");
                    if (priceId != null) {
                        tierId = priceId;
                    }
                }
                
                // Return the models allowed for this tier
                return billingConfig.getModelAccessTiers().getOrDefault(tierId, 
                       billingConfig.getModelAccessTiers().get(billingConfig.getStripeFreeTierId()));
            });
    }
    
    /**
     * Check if a user can use a specific model.
     *
     * @param userId The user ID
     * @param modelName The model name to check
     * @return CompletableFuture that completes with a tuple of (allowed, message, modelList)
     */
    public CompletableFuture<Map<String, Object>> canUseModel(String userId, String modelName) {
        return getAllowedModelsForUser(userId)
            .thenApply(allowedModels -> {
                // Resolve model alias if needed
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
    
    /**
     * Check if a user has sufficient quota to run agents.
     *
     * @param userId The user ID
     * @return CompletableFuture that completes with a tuple of (canRun, message, subscriptionInfo)
     */
    public CompletableFuture<Map<String, Object>> checkBillingStatus(String userId) {
        return getUserSubscription(userId)
            .thenCompose(subscription -> {
                // If no subscription, use free tier
                Map<String, Object> subscriptionInfo = new HashMap<>();
                
                if (subscription == null) {
                    String freeTierId = billingConfig.getStripeFreeTierId();
                    Map<String, Object> freeTierInfo = billingConfig.getSubscriptionTiers().get(freeTierId);
                    
                    subscriptionInfo.put("price_id", freeTierId);
                    subscriptionInfo.put("plan_name", freeTierInfo.get("name"));
                    subscriptionInfo.put("minutes_limit", freeTierInfo.get("minutes"));
                    
                    return CompletableFuture.completedFuture(subscriptionInfo)
                        .thenCompose(info -> calculateMonthlyUsage(userId)
                            .thenApply(usage -> {
                                Map<String, Object> result = new HashMap<>();
                                result.put("subscription", info);
                                
                                int minutesLimit = (Integer) info.get("minutes_limit");
                                
                                if (usage >= minutesLimit) {
                                    result.put("can_run", false);
                                    result.put("message", "Monthly limit of " + minutesLimit + 
                                              " minutes reached. Please upgrade your plan or wait until next month.");
                                } else {
                                    result.put("can_run", true);
                                    result.put("message", "OK");
                                }
                                
                                return result;
                            }));
                }
                
                String priceId = (String) subscription.get("price_id");
                if (priceId == null) {
                    priceId = billingConfig.getStripeFreeTierId();
                }
                
                // Get tier info
                Map<String, Object> tierInfo = billingConfig.getSubscriptionTiers().getOrDefault(
                    priceId, billingConfig.getSubscriptionTiers().get(billingConfig.getStripeFreeTierId()));
                
                subscriptionInfo.put("price_id", priceId);
                subscriptionInfo.put("plan_name", tierInfo.get("name"));
                subscriptionInfo.put("minutes_limit", tierInfo.get("minutes"));
                subscriptionInfo.put("status", subscription.get("status"));
                
                return CompletableFuture.completedFuture(subscriptionInfo)
                    .thenCompose(info -> calculateMonthlyUsage(userId)
                        .thenApply(usage -> {
                            Map<String, Object> result = new HashMap<>();
                            result.put("subscription", info);
                            
                            int minutesLimit = (Integer) info.get("minutes_limit");
                            
                            if (usage >= minutesLimit) {
                                result.put("can_run", false);
                                result.put("message", "Monthly limit of " + minutesLimit + 
                                          " minutes reached. Please upgrade your plan or wait until next month.");
                            } else {
                                result.put("can_run", true);
                                result.put("message", "OK");
                            }
                            
                            return result;
                        }));
            });
    }
    
    /**
     * Create a Stripe Checkout session for a new subscription or update an existing one.
     *
     * @param request The checkout session request
     * @param currentUserId The current user ID
     * @param userEmail The user's email
     * @return CompletableFuture that completes with the session details
     */
    public CompletableFuture<Map<String, Object>> createCheckoutSession(
            CheckoutSessionRequest request, String currentUserId, String userEmail) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Get or create Stripe customer
                String customerId = getStripeCustomerId(currentUserId).join();
                if (customerId == null) {
                    customerId = createStripeCustomer(currentUserId, userEmail).join();
                }
                
                // Check if the price belongs to our product
                Map<String, Object> subscription = getUserSubscription(currentUserId).join();
                
                if (subscription != null) {
                    // Handle subscription update (mock)
                    String currentPriceId = (String) subscription.get("price_id");
                    String newPriceId = request.getPriceId();
                    
                    // Skip if already on this plan
                    if (currentPriceId.equals(newPriceId)) {
                        return Map.of(
                            "subscription_id", "sub_mock_" + UUID.randomUUID().toString().substring(0, 8),
                            "status", "no_change",
                            "message", "Already subscribed to this plan.",
                            "details", Map.of(
                                "is_upgrade", null,
                                "effective_date", null,
                                "current_price", 0,
                                "new_price", 0
                            )
                        );
                    }
                    
                    // Determine if this is an upgrade or downgrade
                    boolean isUpgrade = false;
                    
                    // Compare tier ordering instead of price (simplified for mock)
                    String[] tierOrder = {
                        billingConfig.getStripeFreeTierId(),
                        billingConfig.getStripeStarterTierId(),
                        billingConfig.getStripeProfessionalTierId(),
                        billingConfig.getStripeBusinessTierId()
                    };
                    
                    int currentTierIndex = -1;
                    int newTierIndex = -1;
                    
                    for (int i = 0; i < tierOrder.length; i++) {
                        if (tierOrder[i].equals(currentPriceId)) {
                            currentTierIndex = i;
                        }
                        if (tierOrder[i].equals(newPriceId)) {
                            newTierIndex = i;
                        }
                    }
                    
                    isUpgrade = newTierIndex > currentTierIndex;
                    
                    if (isUpgrade) {
                        // Handle immediate upgrade
                        subscription.put("price_id", newPriceId);
                        subscription.put("status", "active");
                        
                        // Update subscription in memory
                        subscriptions.put(customerId, subscription);
                        
                        return Map.of(
                            "subscription_id", "sub_mock_" + UUID.randomUUID().toString().substring(0, 8),
                            "status", "updated",
                            "message", "Subscription upgraded successfully",
                            "details", Map.of(
                                "is_upgrade", true,
                                "effective_date", "immediate",
                                "current_price", 0,
                                "new_price", 0
                            )
                        );
                    } else {
                        // Handle downgrade via schedule
                        Map<String, Object> scheduleInfo = new HashMap<>();
                        scheduleInfo.put("current_price_id", currentPriceId);
                        scheduleInfo.put("new_price_id", newPriceId);
                        scheduleInfo.put("effective_date", LocalDateTime.now().plusMonths(1));
                        
                        subscription.put("schedule", scheduleInfo);
                        
                        // Update subscription in memory
                        subscriptions.put(customerId, subscription);
                        
                        return Map.of(
                            "subscription_id", "sub_mock_" + UUID.randomUUID().toString().substring(0, 8),
                            "schedule_id", "sch_mock_" + UUID.randomUUID().toString().substring(0, 8),
                            "status", "scheduled",
                            "message", "Subscription downgrade scheduled",
                            "details", Map.of(
                                "is_upgrade", false,
                                "effective_date", "end_of_period",
                                "current_price", 0,
                                "new_price", 0,
                                "effective_at", LocalDateTime.now().plusMonths(1).toString()
                            )
                        );
                    }
                } else {
                    // Create new subscription (mock)
                    String subId = "sub_mock_" + UUID.randomUUID().toString().substring(0, 8);
                    Map<String, Object> newSubscription = new HashMap<>();
                    newSubscription.put("id", subId);
                    newSubscription.put("customer_id", customerId);
                    newSubscription.put("price_id", request.getPriceId());
                    newSubscription.put("status", "active");
                    newSubscription.put("created", LocalDateTime.now());
                    newSubscription.put("current_period_start", LocalDateTime.now());
                    newSubscription.put("current_period_end", LocalDateTime.now().plusMonths(1));
                    
                    // Store subscription in memory
                    subscriptions.put(customerId, newSubscription);
                    
                    String sessionId = "cs_mock_" + UUID.randomUUID().toString().substring(0, 8);
                    String mockUrl = "https://mock-stripe.com/checkout/" + sessionId;
                    
                    return Map.of(
                        "session_id", sessionId,
                        "url", mockUrl,
                        "status", "new"
                    );
                }
            } catch (Exception e) {
                logger.error("Error in mock createCheckoutSession: {}", e.getMessage(), e);
                throw new RuntimeException("Error creating checkout session: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Create a Stripe Customer Portal session for subscription management.
     *
     * @param request The portal session request
     * @param currentUserId The current user ID
     * @return CompletableFuture that completes with the portal session URL
     */
    public CompletableFuture<String> createPortalSession(
            PortalSessionRequest request, String currentUserId) {
        
        return getStripeCustomerId(currentUserId)
            .thenApply(customerId -> {
                if (customerId == null) {
                    throw new RuntimeException("No billing customer found");
                }
                
                String sessionId = "bps_mock_" + UUID.randomUUID().toString().substring(0, 8);
                String mockUrl = "https://mock-stripe.com/billing-portal/" + sessionId + 
                                "?return_url=" + request.getReturnUrl();
                
                logger.info("Mock: Created portal session for customer {}", customerId);
                
                return mockUrl;
            });
    }
    
    /**
     * Get the current subscription status for a user.
     *
     * @param currentUserId The current user ID
     * @return CompletableFuture that completes with the subscription status
     */
    public CompletableFuture<SubscriptionStatus> getSubscription(String currentUserId) {
        return getUserSubscription(currentUserId)
            .thenCompose(subscription -> {
                if (subscription == null) {
                    // Default to free tier
                    String freeTierId = billingConfig.getStripeFreeTierId();
                    Map<String, Object> freeTierInfo = billingConfig.getSubscriptionTiers().get(freeTierId);
                    
                    SubscriptionStatus status = new SubscriptionStatus();
                    status.setStatus("no_subscription");
                    status.setPlanName((String) freeTierInfo.get("name"));
                    status.setPriceId(freeTierId);
                    status.setMinutesLimit((Integer) freeTierInfo.get("minutes"));
                    
                    return CompletableFuture.completedFuture(status);
                }
                
                // Extract current plan details
                String currentPriceId = (String) subscription.get("price_id");
                Map<String, Object> tierInfo = billingConfig.getSubscriptionTiers().getOrDefault(
                    currentPriceId, 
                    Map.of("name", "unknown", "minutes", 0)
                );
                
                return calculateMonthlyUsage(currentUserId)
                    .thenApply(usageMinutes -> {
                        SubscriptionStatus status = new SubscriptionStatus();
                        status.setStatus((String) subscription.get("status"));
                        status.setPlanName((String) tierInfo.get("name"));
                        status.setPriceId(currentPriceId);
                        
                        // Handle dates if present
                        if (subscription.get("current_period_end") instanceof LocalDateTime) {
                            status.setCurrentPeriodEnd((LocalDateTime) subscription.get("current_period_end"));
                        }
                        
                        if (subscription.containsKey("cancel_at_period_end")) {
                            status.setCancelAtPeriodEnd((Boolean) subscription.get("cancel_at_period_end"));
                        }
                        
                        if (subscription.get("trial_end") instanceof LocalDateTime) {
                            status.setTrialEnd((LocalDateTime) subscription.get("trial_end"));
                        }
                        
                        status.setMinutesLimit((Integer) tierInfo.get("minutes"));
                        status.setCurrentUsage(usageMinutes);
                        
                        // Check for scheduled changes
                        if (subscription.get("schedule") instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> schedule = (Map<String, Object>) subscription.get("schedule");
                            
                            String nextPriceId = (String) schedule.get("new_price_id");
                            if (nextPriceId != null) {
                                Map<String, Object> nextTierInfo = billingConfig.getSubscriptionTiers().getOrDefault(
                                    nextPriceId, 
                                    Map.of("name", "unknown")
                                );
                                
                                status.setHasSchedule(true);
                                if ("active".equals(subscription.get("status"))) {
                                    status.setStatus("scheduled_downgrade");
                                }
                                status.setScheduledPlanName((String) nextTierInfo.get("name"));
                                status.setScheduledPriceId(nextPriceId);
                                
                                if (schedule.get("effective_date") instanceof LocalDateTime) {
                                    status.setScheduledChangeDate((LocalDateTime) schedule.get("effective_date"));
                                }
                            }
                        }
                        
                        return status;
                    });
            });
    }
    
    /**
     * Get the list of models available to the user based on their subscription tier.
     *
     * @param currentUserId The current user ID
     * @return CompletableFuture that completes with a map of model information
     */
    public CompletableFuture<Map<String, Object>> getAvailableModels(String currentUserId) {
        return getAllowedModelsForUser(currentUserId)
            .thenCompose(allowedModels -> getUserSubscription(currentUserId)
                .thenApply(subscription -> {
                    // Determine tier name from subscription
                    String tierName = "free";
                    
                    if (subscription != null) {
                        String priceId = (String) subscription.get("price_id");
                        if (priceId != null) {
                            // Get tier info for this price_id
                            Map<String, Object> tierInfo = billingConfig.getSubscriptionTiers().get(priceId);
                            if (tierInfo != null) {
                                tierName = (String) tierInfo.get("name");
                            }
                        }
                    }
                    
                    // Create model info with display names
                    Map<String, String> modelAliases = billingConfig.getModelNameAliases();
                    
                    List<Map<String, Object>> modelInfo = allowedModels.stream()
                        .map(model -> {
                            String displayName = model;
                            String shortName = null;
                            
                            // Find short name for this model
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
                    result.put("subscription_tier", tierName);
                    result.put("total_models", modelInfo.size());
                    
                    return result;
                }));
    }
    
    /**
     * Record usage minutes for a user (for testing purposes).
     *
     * @param userId The user ID
     * @param minutes Minutes to add to usage
     */
    public void recordUsage(String userId, double minutes) {
        usageMinutes.compute(userId, (k, v) -> (v == null ? 0 : v) + minutes);
        logger.info("Mock: Recorded {} minutes of usage for user {}, total: {}", 
                   minutes, userId, usageMinutes.get(userId));
    }
    
    /**
     * Reset all mock data (for testing purposes).
     */
    public void reset() {
        customerIds.clear();
        subscriptions.clear();
        usageMinutes.clear();
        logger.info("Mock: Reset all billing data");
    }
}
