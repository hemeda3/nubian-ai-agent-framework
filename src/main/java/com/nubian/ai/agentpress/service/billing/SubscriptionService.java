package com.nubian.ai.agentpress.service.billing;

import com.nubian.ai.agentpress.model.billing.CheckoutSessionRequest;
import com.nubian.ai.agentpress.model.billing.PortalSessionRequest;
import com.nubian.ai.agentpress.model.billing.SubscriptionStatus;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.stripe.model.Subscription;

import java.util.List;

/**
 * Service interface for subscription management operations.
 */
public interface SubscriptionService {
    
    /**
     * Get the subscription for a user.
     * 
     * @param userId The user ID
     * @return A CompletableFuture with the Stripe subscription object
     */
    CompletableFuture<Subscription> getUserSubscription(String userId);
    
    /**
     * Update a subscription schedule.
     * 
     * @param customerId The customer ID
     * @param items The schedule items
     * @return A CompletableFuture with the schedule
     */
    CompletableFuture<ScheduleResponse> updateSchedule(String customerId, List<Object> items);
    
    /**
     * Create a subscription schedule.
     * 
     * @param customerId The customer ID
     * @param items The schedule items
     * @return A CompletableFuture with the schedule
     */
    CompletableFuture<ScheduleResponse> createSchedule(String customerId, List<Object> items);
    
    /**
     * Response wrapper for subscription schedule operations.
     */
    class ScheduleResponse {
        private String id;
        
        public ScheduleResponse(String id) {
            this.id = id;
        }
        
        public String getId() {
            return id;
        }
    }
    
    /**
     * Get the current subscription status for a customer.
     * 
     * @param customerId The customer ID
     * @return A CompletableFuture with the subscription status
     */
    CompletableFuture<SubscriptionStatus> getSubscriptionStatus(String customerId);
    
    /**
     * Create a checkout session for subscription creation or modification.
     * 
     * @param request The checkout session request
     * @param customerId The customer ID
     * @param email The customer's email
     * @return A CompletableFuture with the checkout session details
     */
    CompletableFuture<Map<String, Object>> createCheckoutSession(
        CheckoutSessionRequest request, 
        String customerId, 
        String email
    );
    
    /**
     * Create a customer portal session for subscription management.
     * 
     * @param request The portal session request
     * @param customerId The customer ID
     * @return A CompletableFuture with the portal session URL
     */
    CompletableFuture<String> createPortalSession(
        PortalSessionRequest request, 
        String customerId
    );
}
