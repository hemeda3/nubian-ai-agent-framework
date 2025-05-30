package com.nubian.ai.agentpress.service.billing;

import com.nubian.ai.config.BillingConfig;
import com.stripe.model.Subscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service for checking billing status and eligibility.
 */
public class BillingStatusService {
    private static final Logger logger = LoggerFactory.getLogger(BillingStatusService.class);
    
    private final SubscriptionService subscriptionService;
    private final UsageService usageService;
    private final BillingConfig billingConfig;
    
    public BillingStatusService(
            SubscriptionService subscriptionService,
            UsageService usageService,
            BillingConfig billingConfig) {
        this.subscriptionService = subscriptionService;
        this.usageService = usageService;
        this.billingConfig = billingConfig;
    }
    
    /**
     * Check if a user has sufficient quota to run agents.
     *
     * @param userId The user ID
     * @return CompletableFuture that completes with a tuple of (canRun, message, subscriptionInfo)
     */
    public CompletableFuture<Map<String, Object>> checkBillingStatus(String userId) {
        return subscriptionService.getUserSubscription(userId)
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
                        .thenCompose(info -> usageService.calculateMonthlyUsage(userId)
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
                
                // Extract subscription details
                String priceId = null;
                if (subscription.getItems() != null && 
                    subscription.getItems().getData() != null && 
                    !subscription.getItems().getData().isEmpty()) {
                    
                    priceId = subscription.getItems().getData().get(0).getPrice().getId();
                }
                
                if (priceId == null) {
                    priceId = billingConfig.getStripeFreeTierId();
                }
                
                // Get tier info
                Map<String, Object> tierInfo = billingConfig.getSubscriptionTiers().getOrDefault(
                    priceId, billingConfig.getSubscriptionTiers().get(billingConfig.getStripeFreeTierId()));
                
                subscriptionInfo.put("price_id", priceId);
                subscriptionInfo.put("plan_name", tierInfo.get("name"));
                subscriptionInfo.put("minutes_limit", tierInfo.get("minutes"));
                subscriptionInfo.put("status", subscription.getStatus());
                
                final String finalPriceId = priceId;
                
                return CompletableFuture.completedFuture(subscriptionInfo)
                    .thenCompose(info -> usageService.calculateMonthlyUsage(userId)
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
     * Check if a subscription is active.
     *
     * @param subscription The subscription to check
     * @return true if the subscription is active or trialing
     */
    public boolean isSubscriptionActive(Subscription subscription) {
        if (subscription == null) {
            return false;
        }
        
        String status = subscription.getStatus();
        return "active".equals(status) || "trialing".equals(status);
    }
}
