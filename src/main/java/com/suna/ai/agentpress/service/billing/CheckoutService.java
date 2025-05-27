package com.Nubian.ai.agentpress.service.billing;

import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;

import com.Nubian.ai.config.BillingConfig;
import com.Nubian.ai.agentpress.model.billing.CheckoutSessionRequest;
import com.Nubian.ai.agentpress.service.DBConnection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service for handling Stripe checkout sessions and subscription changes.
 */
public class CheckoutService {
    private static final Logger logger = LoggerFactory.getLogger(CheckoutService.class);
    
    private final CustomerManagementService customerService;
    private final SubscriptionService subscriptionService;
    private final BillingConfig billingConfig;
    private final DBConnection dbConnection;
    
    public CheckoutService(
            CustomerManagementService customerService,
            SubscriptionService subscriptionService,
            BillingConfig billingConfig,
            DBConnection dbConnection) {
        this.customerService = customerService;
        this.subscriptionService = subscriptionService;
        this.billingConfig = billingConfig;
        this.dbConnection = dbConnection;
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
                String customerId = customerService.getStripeCustomerId(currentUserId).join();
                if (customerId == null) {
                    customerId = customerService.createStripeCustomer(currentUserId, userEmail).join();
                }
                
                // Get target price
                try {
                    com.stripe.model.Price price = com.stripe.model.Price.retrieve(request.getPriceId());
                    String productId = price.getProduct();
                    
                    // Verify the price belongs to our product
                    if (!billingConfig.getStripeProductId().equals(productId)) {
                        throw new IllegalArgumentException("Price ID does not belong to the correct product.");
                    }
                    
                    // Check for existing subscription
                    Subscription existingSubscription = subscriptionService.getUserSubscription(currentUserId).join();
                    
                    if (existingSubscription != null) {
                        // Handle subscription update
                        return handleSubscriptionUpdate(existingSubscription, request.getPriceId(), customerId);
                    } else {
                        // Create new subscription via checkout
                        return createNewSubscription(customerId, request);
                    }
                    
                } catch (StripeException e) {
                    logger.error("Error retrieving Stripe price: {}", e.getMessage());
                    throw new RuntimeException("Invalid price ID: " + request.getPriceId(), e);
                }
                
            } catch (Exception e) {
                logger.error("Error creating checkout session: {}", e.getMessage(), e);
                throw new RuntimeException("Error creating checkout session: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Handle updating an existing subscription.
     *
     * @param existingSubscription The existing subscription
     * @param newPriceId The new price ID
     * @param customerId The customer ID
     * @return Map with update status
     */
    private Map<String, Object> handleSubscriptionUpdate(
            Subscription existingSubscription, String newPriceId, String customerId) throws StripeException {
        
        String subscriptionId = existingSubscription.getId();
        
        if (existingSubscription.getItems() == null || 
            existingSubscription.getItems().getData() == null || 
            existingSubscription.getItems().getData().isEmpty()) {
            throw new RuntimeException("Subscription items not found");
        }
        
        String currentPriceId = existingSubscription.getItems().getData().get(0).getPrice().getId();
        
        // Skip if already on this plan
        if (currentPriceId.equals(newPriceId)) {
            return Map.of(
                "subscription_id", subscriptionId,
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
        
        // Get current and new price details
        com.stripe.model.Price currentPrice = com.stripe.model.Price.retrieve(currentPriceId);
        com.stripe.model.Price newPrice = com.stripe.model.Price.retrieve(newPriceId);
        
        boolean isUpgrade = newPrice.getUnitAmount() > currentPrice.getUnitAmount();
        
        if (isUpgrade) {
            // Handle immediate upgrade
            Subscription updatedSubscription = Subscription.retrieve(subscriptionId);
            Map<String, Object> params = new HashMap<>();
            params.put("items", List.of(
                Map.of(
                    "id", existingSubscription.getItems().getData().get(0).getId(),
                    "price", newPriceId
                )
            ));
            params.put("proration_behavior", "always_invoice");
            
            updatedSubscription = updatedSubscription.update(params);
            
            // Update active status in database
            customerService.updateCustomerStatus(customerId, true).join();
            
            return Map.of(
                "subscription_id", updatedSubscription.getId(),
                "status", "updated",
                "message", "Subscription upgraded successfully",
                "details", Map.of(
                    "is_upgrade", true,
                    "effective_date", "immediate",
                    "current_price", currentPrice.getUnitAmount() / 100.0,
                    "new_price", newPrice.getUnitAmount() / 100.0
                )
            );
            
        } else {
            // Handle downgrade via schedule
            try {
                Long currentPeriodEnd = existingSubscription.getEndedAt();
                
                // Check for existing schedule
                String scheduleId = existingSubscription.getSchedule();
                
                List<Object> phases = List.of(
                    // Current phase
                    Map.of(
                        "items", List.of(Map.of("price", currentPriceId, "quantity", 1)),
                        "start_date", existingSubscription.getStartDate(),
                        "end_date", currentPeriodEnd,
                        "proration_behavior", "none"
                    ),
                    // New phase (downgrade)
                    Map.of(
                        "items", List.of(Map.of("price", newPriceId, "quantity", 1)),
                        "start_date", currentPeriodEnd,
                        "proration_behavior", "none"
                    )
                );
                
                if (scheduleId != null) {
                    // Update existing schedule
                    SubscriptionService.ScheduleResponse schedule = subscriptionService.updateSchedule(scheduleId, phases).join();
                    
                    return Map.of(
                        "subscription_id", subscriptionId,
                        "schedule_id", schedule.getId(),
                        "status", "scheduled",
                        "message", "Subscription downgrade scheduled",
                        "details", Map.of(
                            "is_upgrade", false,
                            "effective_date", "end_of_period",
                            "current_price", currentPrice.getUnitAmount() / 100.0,
                            "new_price", newPrice.getUnitAmount() / 100.0,
                            "effective_at", LocalDateTime.ofEpochSecond(currentPeriodEnd, 0, ZoneOffset.UTC).toString()
                        )
                    );
                } else {
                    // Create new schedule
                    SubscriptionService.ScheduleResponse schedule = subscriptionService.createSchedule(subscriptionId, phases).join();
                    
                    return Map.of(
                        "subscription_id", subscriptionId,
                        "schedule_id", schedule.getId(),
                        "status", "scheduled",
                        "message", "Subscription downgrade scheduled",
                        "details", Map.of(
                            "is_upgrade", false,
                            "effective_date", "end_of_period",
                            "current_price", currentPrice.getUnitAmount() / 100.0,
                            "new_price", newPrice.getUnitAmount() / 100.0,
                            "effective_at", LocalDateTime.ofEpochSecond(currentPeriodEnd, 0, ZoneOffset.UTC).toString()
                        )
                    );
                }
                
            } catch (Exception e) {
                logger.error("Error handling subscription schedule: {}", e.getMessage(), e);
                throw new RuntimeException("Error handling subscription schedule: " + e.getMessage(), e);
            }
        }
    }
    
    /**
     * Create a new subscription via Checkout.
     *
     * @param customerId The customer ID
     * @param request The checkout session request
     * @return Map with session details
     */
    private Map<String, Object> createNewSubscription(
            String customerId, CheckoutSessionRequest request) throws StripeException {
        
        SessionCreateParams params = SessionCreateParams.builder()
            .setCustomer(customerId)
            .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
            .addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setPrice(request.getPriceId())
                    .setQuantity(1L)
                    .build()
            )
            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
            .setSuccessUrl(request.getSuccessUrl())
            .setCancelUrl(request.getCancelUrl())
            .putMetadata("product_id", billingConfig.getStripeProductId())
            .build();
        
        Session session = Session.create(params);
        
        // Update customer status to potentially active
        customerService.updateCustomerStatus(customerId, true).join();
        
        return Map.of(
            "session_id", session.getId(),
            "url", session.getUrl(),
            "status", "new"
        );
    }
}
