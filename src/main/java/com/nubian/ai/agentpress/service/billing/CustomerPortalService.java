package com.nubian.ai.agentpress.service.billing;

import com.stripe.exception.StripeException;
import com.nubian.ai.agentpress.model.billing.PortalSessionRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service for managing Stripe customer portal sessions.
 */
public class CustomerPortalService {
    private static final Logger logger = LoggerFactory.getLogger(CustomerPortalService.class);
    
    private final CustomerManagementService customerService;
    
    public CustomerPortalService(CustomerManagementService customerService) {
        this.customerService = customerService;
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
        
        return customerService.getStripeCustomerId(currentUserId)
            .thenApply(customerId -> {
                if (customerId == null) {
                    throw new RuntimeException("No billing customer found");
                }
                
                try {
                    Map<String, Object> params = new HashMap<>();
                    params.put("customer", customerId);
                    params.put("return_url", request.getReturnUrl());
                    
                    com.stripe.model.billingportal.Session session = 
                        com.stripe.model.billingportal.Session.create(params);
                    
                    return session.getUrl();
                } catch (StripeException e) {
                    logger.error("Error creating portal session: {}", e.getMessage(), e);
                    throw new RuntimeException("Error creating portal session: " + e.getMessage(), e);
                }
            });
    }
}
