package com.Nubian.ai.agentpress.service.billing;

import com.stripe.exception.StripeException;
import com.stripe.model.SubscriptionSchedule;
import com.Nubian.ai.config.BillingConfig;
import com.Nubian.ai.agentpress.model.billing.SubscriptionStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service for managing subscription status information.
 */
public class SubscriptionStatusService {
    private static final Logger logger = LoggerFactory.getLogger(SubscriptionStatusService.class);
    
    private final SubscriptionService subscriptionService;
    private final UsageService usageService;
    private final BillingConfig billingConfig;
    
    public SubscriptionStatusService(
            SubscriptionService subscriptionService,
            UsageService usageService,
            BillingConfig billingConfig) {
        this.subscriptionService = subscriptionService;
        this.usageService = usageService;
        this.billingConfig = billingConfig;
    }
    
    /**
     * Get the current subscription status for a user.
     *
     * @param currentUserId The current user ID
     * @return CompletableFuture that completes with the subscription status
     */
    public CompletableFuture<SubscriptionStatus> getSubscription(String currentUserId) {
        return subscriptionService.getUserSubscription(currentUserId)
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
                String currentPriceId = subscription.getItems().getData().get(0).getPrice().getId();
                Map<String, Object> tierInfo = billingConfig.getSubscriptionTiers().getOrDefault(
                    currentPriceId, 
                    Map.of("name", "unknown", "minutes", 0)
                );
                
                return usageService.calculateMonthlyUsage(currentUserId)
                    .thenApply(usageMinutes -> {
                        SubscriptionStatus status = new SubscriptionStatus();
                        status.setStatus(subscription.getStatus());
                        status.setPlanName((String) tierInfo.get("name"));
                        status.setPriceId(currentPriceId);
                        
                        if (subscription.getEndedAt() != null) {
                            status.setCurrentPeriodEnd(
                                LocalDateTime.ofEpochSecond(subscription.getEndedAt(), 0, ZoneOffset.UTC)
                            );
                        }
                        
                        status.setCancelAtPeriodEnd(subscription.getCancelAtPeriodEnd());
                        
                        if (subscription.getTrialEnd() != null) {
                            status.setTrialEnd(
                                LocalDateTime.ofEpochSecond(subscription.getTrialEnd(), 0, ZoneOffset.UTC)
                            );
                        }
                        
                        status.setMinutesLimit((Integer) tierInfo.get("minutes"));
                        status.setCurrentUsage(usageMinutes);
                        
                        // Check for scheduled changes
                        String scheduleId = subscription.getSchedule();
                        if (scheduleId != null) {
                            try {
                                SubscriptionSchedule schedule = SubscriptionSchedule.retrieve(scheduleId);
                                List<SubscriptionSchedule.Phase> phases = schedule.getPhases();
                                
                                if (phases.size() > 1) {
                                    // Find the next phase
                                    SubscriptionSchedule.Phase nextPhase = phases.get(1);
                                    String nextPriceId = nextPhase.getItems().get(0).getPrice();
                                    Map<String, Object> nextTierInfo = billingConfig.getSubscriptionTiers().getOrDefault(
                                        nextPriceId, 
                                        Map.of("name", "unknown")
                                    );
                                    
                                    status.setHasSchedule(true);
                                    if ("active".equals(subscription.getStatus())) {
                                        status.setStatus("scheduled_downgrade");
                                    }
                                    status.setScheduledPlanName((String) nextTierInfo.get("name"));
                                    status.setScheduledPriceId(nextPriceId);
                                    
                                    if (nextPhase.getStartDate() != null) {
                                        status.setScheduledChangeDate(
                                            LocalDateTime.ofEpochSecond(nextPhase.getStartDate(), 0, ZoneOffset.UTC)
                                        );
                                    }
                                }
                                
                            } catch (StripeException e) {
                                logger.error("Error retrieving subscription schedule: {}", e.getMessage(), e);
                                // Proceed without schedule info if retrieval fails
                            }
                        }
                        
                        return status;
                    });
            });
    }
}
