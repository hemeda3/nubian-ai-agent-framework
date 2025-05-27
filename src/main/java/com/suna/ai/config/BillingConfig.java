package com.Nubian.ai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Configuration properties for billing functionality.
 * Contains Stripe API keys, subscription tier IDs, and tier definitions.
 */
@Configuration
@ConfigurationProperties(prefix = "billing")
public class BillingConfig {
    private static final Logger logger = LoggerFactory.getLogger(BillingConfig.class);
    
    // Stripe API configuration
    private String stripeSecretKey;
    private String stripeWebhookSecret;
    private String stripeProductId;
    
    // Stripe tier IDs
    private String stripeFreeTierId;
    private String stripeStarterTierId;
    private String stripeProfessionalTierId;
    private String stripeBusinessTierId;
    private String stripeUsagePriceId; // Add this field
    
    // Model name aliases for mapping
    private Map<String, String> modelNameAliases = new HashMap<>();
    
    // Subscription tiers info (initialized in initSubscriptionTiers)
    private Map<String, Map<String, Object>> subscriptionTiers = new HashMap<>();
    
    // Model access tiers for each subscription level
    private Map<String, Set<String>> modelAccessTiers = new HashMap<>();
    
    /**
     * Initialize the tier definitions after properties are set.
     */
    @PostConstruct
    public void initSubscriptionTiers() {
        logger.info("Initializing billing subscription tiers");
        
        // Define free tier
        Map<String, Object> freeTier = new HashMap<>();
        freeTier.put("name", "Free");
        freeTier.put("minutes", 100);
        freeTier.put("price_usd", 0);
        subscriptionTiers.put(stripeFreeTierId, freeTier);
        
        // Define starter tier
        Map<String, Object> starterTier = new HashMap<>();
        starterTier.put("name", "Starter");
        starterTier.put("minutes", 500);
        starterTier.put("price_usd", 9.99);
        subscriptionTiers.put(stripeStarterTierId, starterTier);
        
        // Define professional tier
        Map<String, Object> professionalTier = new HashMap<>();
        professionalTier.put("name", "Professional");
        professionalTier.put("minutes", 2000);
        professionalTier.put("price_usd", 29.99);
        subscriptionTiers.put(stripeProfessionalTierId, professionalTier);
        
        // Define business tier
        Map<String, Object> businessTier = new HashMap<>();
        businessTier.put("name", "Business");
        businessTier.put("minutes", 10000);
        businessTier.put("price_usd", 99.99);
        subscriptionTiers.put(stripeBusinessTierId, businessTier);
        
        // Initialize model access tiers (models allowed for each subscription level)
        initModelAccessTiers();
        
        // Initialize model name aliases
        initModelNameAliases();
        
        logger.info("Billing subscription tiers initialized");
    }
    

    
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
    /**
     * Initialize model access tiers.
     * These define which models are available for each subscription level.
     */
    private void initModelAccessTiers() {
        // Free tier models
        Set<String> freeModels = Set.of(
            "gpt-3.5-turbo", 
            "claude-instant-1.2"
        );
        modelAccessTiers.put(stripeFreeTierId, freeModels);
        
        // Starter tier models
        Set<String> starterModels = Set.of(
            "gpt-3.5-turbo", 
            "gpt-3.5-turbo-16k", 
            "claude-instant-1.2",
            "gemini-pro"
        );
        modelAccessTiers.put(stripeStarterTierId, starterModels);
        
        // Professional tier models
        Set<String> professionalModels = Set.of(
            "gpt-3.5-turbo", 
            "gpt-3.5-turbo-16k",
            "gpt-4-turbo", 
            "claude-instant-1.2", 
            "claude-2",
            "gemini-pro"
        );
        modelAccessTiers.put(stripeProfessionalTierId, professionalModels);
        
        // Business tier models (all models)
        Set<String> businessModels = Set.of(
            "gpt-3.5-turbo", 
            "gpt-3.5-turbo-16k",
            "gpt-4-turbo", 
            "gpt-4-turbo-vision",
            "claude-instant-1.2", 
            "claude-2",
            "claude-3-opus",
            "claude-3-sonnet",
            "claude-3-haiku",
            "gemini-pro",
            "gemini-pro-vision"
        );
        modelAccessTiers.put(stripeBusinessTierId, businessModels);
    }
    
    /**
     * Initialize model name aliases.
     * These map user-friendly names to the actual model identifiers.
     */
    private void initModelNameAliases() {
        // OpenAI aliases
        modelNameAliases.put("gpt-3.5", "gpt-3.5-turbo");
        modelNameAliases.put("gpt-4", "gpt-4-turbo");
        
        // Anthropic aliases
        modelNameAliases.put("claude", "claude-2");
        modelNameAliases.put("claude-instant", "claude-instant-1.2");
        
        // Google aliases
        modelNameAliases.put("gemini", "gemini-pro");
    }
    
    // Getters and setters
    
    public String getStripeSecretKey() {
        return stripeSecretKey;
    }
    
    public void setStripeSecretKey(String stripeSecretKey) {
        this.stripeSecretKey = stripeSecretKey;
    }
    
    public String getStripeWebhookSecret() {
        return stripeWebhookSecret;
    }
    
    public void setStripeWebhookSecret(String stripeWebhookSecret) {
        this.stripeWebhookSecret = stripeWebhookSecret;
    }
    
    public String getStripeProductId() {
        return stripeProductId;
    }
    
    public void setStripeProductId(String stripeProductId) {
        this.stripeProductId = stripeProductId;
    }
    
    public String getStripeFreeTierId() {
        return stripeFreeTierId;
    }
    
    public void setStripeFreeTierId(String stripeFreeTierId) {
        this.stripeFreeTierId = stripeFreeTierId;
    }
    
    public String getStripeStarterTierId() {
        return stripeStarterTierId;
    }
    
    public void setStripeStarterTierId(String stripeStarterTierId) {
        this.stripeStarterTierId = stripeStarterTierId;
    }
    
    public String getStripeProfessionalTierId() {
        return stripeProfessionalTierId;
    }
    
    public void setStripeProfessionalTierId(String stripeProfessionalTierId) {
        this.stripeProfessionalTierId = stripeProfessionalTierId;
    }
    
    public String getStripeBusinessTierId() {
        return stripeBusinessTierId;
    }
    
    public void setStripeBusinessTierId(String stripeBusinessTierId) {
        this.stripeBusinessTierId = stripeBusinessTierId;
    }
    
    public String getStripeUsagePriceId() { // Getter for stripeUsagePriceId
        return stripeUsagePriceId;
    }

    public void setStripeUsagePriceId(String stripeUsagePriceId) { // Setter for stripeUsagePriceId
        this.stripeUsagePriceId = stripeUsagePriceId;
    }

    public Map<String, Map<String, Object>> getSubscriptionTiers() {
        return subscriptionTiers;
    }
    
    public Map<String, Set<String>> getModelAccessTiers() {
        return modelAccessTiers;
    }
    
    public Map<String, String> getModelNameAliases() {
        return modelNameAliases;
    }
    
    /**
     * Get the canonical model name from a user-provided alias.
     * 
     * @param modelName The user-provided model name
     * @return The canonical model name
     */
    public String getCanonicalModelName(String modelName) {
        return modelNameAliases.getOrDefault(modelName, modelName);
    }
}
