package com.Nubian.ai.agentpress.service.impl;

import com.Nubian.ai.config.BillingConfig;
import com.Nubian.ai.agentpress.model.billing.CheckoutSessionRequest;
import com.Nubian.ai.agentpress.model.billing.PortalSessionRequest;
import com.Nubian.ai.agentpress.model.billing.SubscriptionStatus;
import com.Nubian.ai.agentpress.service.BillingService;
import com.Nubian.ai.agentpress.service.DBConnection;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * DRAFT Implementation of a Stripe billing service - NOT USED IN PRODUCTION
 * This is a placeholder/draft for when the Stripe Java SDK is available.
 * 
 * NOTE: This file is kept for reference but is not active in the application.
 * See MockStripeBillingService for the active implementation.
 */
@Service
@Profile("never") // This profile doesn't exist, so this bean will never be created
@Slf4j
public class StripeBillingServiceDraft implements BillingService {

    private final DBConnection dbConnection;
    private final BillingConfig billingConfig;

    @Value("${stripe.api.key:missing}")
    private String stripeApiKey;

    @Autowired
    public StripeBillingServiceDraft(DBConnection dbConnection, BillingConfig billingConfig) {
        this.dbConnection = dbConnection;
        this.billingConfig = billingConfig;
        log.info("StripeBillingServiceDraft initialized - THIS IS NOT ACTIVE");
    }

    @Override
    public CompletableFuture<Map<String, Object>> checkModelAccess(String userId, String modelName) {
        // This is a draft implementation
        return CompletableFuture.completedFuture(Map.of("allowed", true, "message", "Draft implementation"));
    }

    @Override
    public CompletableFuture<Set<String>> getAccessibleModels(String userId) {
        // This is a draft implementation
        return CompletableFuture.completedFuture(Set.of("gpt-4", "gpt-3.5-turbo"));
    }

    @Override
    public CompletableFuture<Map<String, Object>> getModelDetails(String userId) {
        // This is a draft implementation
        return CompletableFuture.completedFuture(Map.of("models", List.of(), "subscription_tier", "free"));
    }

    @Override
    public CompletableFuture<Map<String, Object>> checkBillingStatus(String userId) {
        // This is a draft implementation
        return CompletableFuture.completedFuture(Map.of("can_run", true, "message", "Draft implementation"));
    }

    @Override
    public CompletableFuture<SubscriptionStatus> getSubscriptionStatus(String userId) {
        // This is a draft implementation
        SubscriptionStatus status = new SubscriptionStatus();
        status.setStatus("no_subscription");
        status.setPlanName("free");
        status.setCurrentUsage(0.0);
        return CompletableFuture.completedFuture(status);
    }

    @Override
    public CompletableFuture<Map<String, Object>> createCheckoutSession(
            CheckoutSessionRequest request, String userId, String email) {
        // This is a draft implementation
        return CompletableFuture.completedFuture(Map.of("url", "https://example.com/checkout"));
    }

    @Override
    public CompletableFuture<String> createPortalSession(PortalSessionRequest request, String userId) {
        // This is a draft implementation
        return CompletableFuture.completedFuture("https://example.com/portal");
    }

    @Override
    public void recordUsage(String userId, String runId, Instant startTime, Instant endTime, String modelName) {
        // This is a draft implementation - just log it
        log.info("Draft: Recording usage for user {} (run {}) on model {}", userId, runId, modelName);
    }

    @Override
    public CompletableFuture<Double> calculateMonthlyUsage(String userId) {
        // This is a draft implementation
        return CompletableFuture.completedFuture(0.0);
    }
}
