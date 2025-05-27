package com.Nubian.ai.agentpress.util.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Centralized configuration for AgentPress backend.
 * This class loads environment variables and provides type checking and validation.
 */
@Component
public class Configuration {
    private static final Logger logger = LoggerFactory.getLogger(Configuration.class);

    // Environment mode
    @Value("${ENV_MODE:local}")
    private String envModeStr;
    private EnvMode envMode;

    // Subscription tier IDs - Production
    private final String STRIPE_FREE_TIER_ID_PROD = "price_1RILb4G6l1KZGqIrK4QLrx9i";
    private final String STRIPE_TIER_2_20_ID_PROD = "price_1RILb4G6l1KZGqIrhomjgDnO";
    private final String STRIPE_TIER_6_50_ID_PROD = "price_1RILb4G6l1KZGqIr5q0sybWn";
    private final String STRIPE_TIER_12_100_ID_PROD = "price_1RILb4G6l1KZGqIr5Y20ZLHm";
    private final String STRIPE_TIER_25_200_ID_PROD = "price_1RILb4G6l1KZGqIrGAD8rNjb";
    private final String STRIPE_TIER_50_400_ID_PROD = "price_1RILb4G6l1KZGqIruNBUMTF1";
    private final String STRIPE_TIER_125_800_ID_PROD = "price_1RILb3G6l1KZGqIrbJA766tN";
    private final String STRIPE_TIER_200_1000_ID_PROD = "price_1RILb3G6l1KZGqIrmauYPOiN";

    // Subscription tier IDs - Staging
    private final String STRIPE_FREE_TIER_ID_STAGING = "price_1RIGvuG6l1KZGqIrw14abxeL";
    private final String STRIPE_TIER_2_20_ID_STAGING = "price_1RIGvuG6l1KZGqIrCRu0E4Gi";
    private final String STRIPE_TIER_6_50_ID_STAGING = "price_1RIGvuG6l1KZGqIrvjlz5p5V";
    private final String STRIPE_TIER_12_100_ID_STAGING = "price_1RIGvuG6l1KZGqIrT6UfgblC";
    private final String STRIPE_TIER_25_200_ID_STAGING = "price_1RIGvuG6l1KZGqIrOVLKlOMj";
    private final String STRIPE_TIER_50_400_ID_STAGING = "price_1RIKNgG6l1KZGqIrvsat5PW7";
    private final String STRIPE_TIER_125_800_ID_STAGING = "price_1RIKNrG6l1KZGqIrjKT0yGvI";
    private final String STRIPE_TIER_200_1000_ID_STAGING = "price_1RIKQ2G6l1KZGqIrum9n8SI7";

    // LLM API keys
    @Value("${ANTHROPIC_API_KEY:#{null}}")
    private String anthropicApiKey;

    @Value("${OPENAI_API_KEY:#{null}}")
    private String openaiApiKey;

    @Value("${GROQ_API_KEY:#{null}}")
    private String groqApiKey;

    @Value("${OPENROUTER_API_KEY:#{null}}")
    private String openrouterApiKey;

    @Value("${OPENROUTER_API_BASE:https://openrouter.ai/api/v1}")
    private String openrouterApiBase;

    @Value("${OR_SITE_URL:https://kortix.ai}")
    private String orSiteUrl;

    @Value("${OR_APP_NAME:Kortix AI}")
    private String orAppName;

    // AWS Bedrock credentials
    @Value("${AWS_ACCESS_KEY_ID:#{null}}")
    private String awsAccessKeyId;

    @Value("${AWS_SECRET_ACCESS_KEY:#{null}}")
    private String awsSecretAccessKey;

    @Value("${AWS_REGION_NAME:#{null}}")
    private String awsRegionName;

    // Model configuration
    @Value("${MODEL_TO_USE:anthropic/claude-3-7-sonnet-latest}")
    private String modelToUse;

    // Supabase configuration
    @Value("${SUPABASE_URL}")
    private String supabaseUrl;

    @Value("${SUPABASE_ANON_KEY}")
    private String supabaseAnonKey;

    @Value("${SUPABASE_SERVICE_ROLE_KEY}")
    private String supabaseServiceRoleKey;

    // Redis configuration
    @Value("${REDIS_HOST}")
    private String redisHost;

    @Value("${REDIS_PORT:6379}")
    private int redisPort;

    @Value("${REDIS_PASSWORD}")
    private String redisPassword;

    @Value("${REDIS_SSL:true}")
    private boolean redisSsl;

    // Daytona sandbox configuration
    @Value("${DAYTONA_API_KEY}")
    private String daytonaApiKey;

    @Value("${DAYTONA_SERVER_URL}")
    private String daytonaServerUrl;

    @Value("${DAYTONA_TARGET}")
    private String daytonaTarget;

    // Search and other API keys
    @Value("${TAVILY_API_KEY}")
    private String tavilyApiKey;

    @Value("${RAPID_API_KEY}")
    private String rapidApiKey;

    @Value("${CLOUDFLARE_API_TOKEN:#{null}}")
    private String cloudflareApiToken;

    @Value("${FIRECRAWL_API_KEY}")
    private String firecrawlApiKey;

    @Value("${FIRECRAWL_URL:https://api.firecrawl.dev}")
    private String firecrawlUrl;

    // Stripe configuration
    @Value("${STRIPE_SECRET_KEY:#{null}}")
    private String stripeSecretKey;

    @Value("${STRIPE_WEBHOOK_SECRET:#{null}}")
    private String stripeWebhookSecret;

    @Value("${STRIPE_DEFAULT_PLAN_ID:#{null}}")
    private String stripeDefaultPlanId;

    @Value("${STRIPE_DEFAULT_TRIAL_DAYS:14}")
    private int stripeDefaultTrialDays;

    // Stripe Product IDs
    private final String STRIPE_PRODUCT_ID_PROD = "prod_SCl7AQ2C8kK1CD";
    private final String STRIPE_PRODUCT_ID_STAGING = "prod_SCgIj3G7yPOAWY";

    // Sandbox configuration
    @Value("${SANDBOX_IMAGE_NAME:kortix/Nubian:0.1.2.8}")
    private String sandboxImageName;

    @Value("${SANDBOX_ENTRYPOINT:/usr/bin/supervisord -n -c /etc/supervisor/conf.d/supervisord.conf}")
    private String sandboxEntrypoint;

    /**
     * Initialize the configuration after dependency injection.
     */
    @PostConstruct
    public void init() {
        // Parse environment mode
        this.envMode = EnvMode.fromString(envModeStr);
        
        logger.info("Environment mode: {}", envMode.getValue());
        
        // Validate required configuration
        validate();
    }
    
    /**
     * Validate required configuration values.
     * Throws an exception if any required fields are missing.
     */
    private void validate() {
        // Example validation (add more as needed)
        if (supabaseUrl == null || supabaseUrl.isEmpty()) {
            throw new IllegalStateException("Missing required configuration: SUPABASE_URL");
        }
        
        if (supabaseAnonKey == null || supabaseAnonKey.isEmpty()) {
            throw new IllegalStateException("Missing required configuration: SUPABASE_ANON_KEY");
        }
        
        if (supabaseServiceRoleKey == null || supabaseServiceRoleKey.isEmpty()) {
            throw new IllegalStateException("Missing required configuration: SUPABASE_SERVICE_ROLE_KEY");
        }
    }

    /**
     * Get the current environment mode.
     *
     * @return The current environment mode
     */
    public EnvMode getEnvMode() {
        return envMode;
    }

    /**
     * Get the appropriate Stripe Free Tier ID for the current environment.
     *
     * @return The Stripe Free Tier ID
     */
    public String getStripeFreeTierId() {
        return envMode == EnvMode.STAGING ? STRIPE_FREE_TIER_ID_STAGING : STRIPE_FREE_TIER_ID_PROD;
    }

    /**
     * Get the appropriate Stripe Tier 2 ID for the current environment.
     *
     * @return The Stripe Tier 2 ID
     */
    public String getStripeTier2_20Id() {
        return envMode == EnvMode.STAGING ? STRIPE_TIER_2_20_ID_STAGING : STRIPE_TIER_2_20_ID_PROD;
    }

    /**
     * Get the appropriate Stripe Tier 6 ID for the current environment.
     *
     * @return The Stripe Tier 6 ID
     */
    public String getStripeTier6_50Id() {
        return envMode == EnvMode.STAGING ? STRIPE_TIER_6_50_ID_STAGING : STRIPE_TIER_6_50_ID_PROD;
    }

    /**
     * Get the appropriate Stripe Tier 12 ID for the current environment.
     *
     * @return The Stripe Tier 12 ID
     */
    public String getStripeTier12_100Id() {
        return envMode == EnvMode.STAGING ? STRIPE_TIER_12_100_ID_STAGING : STRIPE_TIER_12_100_ID_PROD;
    }

    /**
     * Get the appropriate Stripe Tier 25 ID for the current environment.
     *
     * @return The Stripe Tier 25 ID
     */
    public String getStripeTier25_200Id() {
        return envMode == EnvMode.STAGING ? STRIPE_TIER_25_200_ID_STAGING : STRIPE_TIER_25_200_ID_PROD;
    }

    /**
     * Get the appropriate Stripe Tier 50 ID for the current environment.
     *
     * @return The Stripe Tier 50 ID
     */
    public String getStripeTier50_400Id() {
        return envMode == EnvMode.STAGING ? STRIPE_TIER_50_400_ID_STAGING : STRIPE_TIER_50_400_ID_PROD;
    }

    /**
     * Get the appropriate Stripe Tier 125 ID for the current environment.
     *
     * @return The Stripe Tier 125 ID
     */
    public String getStripeTier125_800Id() {
        return envMode == EnvMode.STAGING ? STRIPE_TIER_125_800_ID_STAGING : STRIPE_TIER_125_800_ID_PROD;
    }

    /**
     * Get the appropriate Stripe Tier 200 ID for the current environment.
     *
     * @return The Stripe Tier 200 ID
     */
    public String getStripeTier200_1000Id() {
        return envMode == EnvMode.STAGING ? STRIPE_TIER_200_1000_ID_STAGING : STRIPE_TIER_200_1000_ID_PROD;
    }

    /**
     * Get the appropriate Stripe Product ID for the current environment.
     *
     * @return The Stripe Product ID
     */
    public String getStripeProductId() {
        return envMode == EnvMode.STAGING ? STRIPE_PRODUCT_ID_STAGING : STRIPE_PRODUCT_ID_PROD;
    }

    /**
     * Get a configuration value by key with an optional default.
     *
     * @param key The configuration key
     * @param defaultValue The default value if the key is not found
     * @return The configuration value
     */
    public Object get(String key, Object defaultValue) {
        try {
            return Optional.ofNullable(this.getClass().getDeclaredField(key).get(this)).orElse(defaultValue);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            logger.warn("Configuration key not found: {}", key);
            return defaultValue;
        }
    }

    /**
     * Return the configuration as a map.
     *
     * @return A map representation of the configuration
     */
    public Map<String, Object> asMap() {
        Map<String, Object> configMap = new HashMap<>();
        
        for (java.lang.reflect.Field field : this.getClass().getDeclaredFields()) {
            try {
                field.setAccessible(true);
                configMap.put(field.getName(), field.get(this));
            } catch (IllegalAccessException e) {
                logger.warn("Could not access field: {}", field.getName());
            }
        }
        
        return configMap;
    }

    // Getters for all configuration properties

    public String getAnthropicApiKey() {
        return anthropicApiKey;
    }

    public String getOpenaiApiKey() {
        return openaiApiKey;
    }

    public String getGroqApiKey() {
        return groqApiKey;
    }

    public String getOpenrouterApiKey() {
        return openrouterApiKey;
    }

    public String getOpenrouterApiBase() {
        return openrouterApiBase;
    }

    public String getOrSiteUrl() {
        return orSiteUrl;
    }

    public String getOrAppName() {
        return orAppName;
    }

    public String getAwsAccessKeyId() {
        return awsAccessKeyId;
    }

    public String getAwsSecretAccessKey() {
        return awsSecretAccessKey;
    }

    public String getAwsRegionName() {
        return awsRegionName;
    }

    public String getModelToUse() {
        return modelToUse;
    }

    public String getSupabaseUrl() {
        return supabaseUrl;
    }

    public String getSupabaseAnonKey() {
        return supabaseAnonKey;
    }

    public String getSupabaseServiceRoleKey() {
        return supabaseServiceRoleKey;
    }

    public String getRedisHost() {
        return redisHost;
    }

    public int getRedisPort() {
        return redisPort;
    }

    public String getRedisPassword() {
        return redisPassword;
    }

    public boolean isRedisSsl() {
        return redisSsl;
    }

    public String getDaytonaApiKey() {
        return daytonaApiKey;
    }

    public String getDaytonaServerUrl() {
        return daytonaServerUrl;
    }

    public String getDaytonaTarget() {
        return daytonaTarget;
    }

    public String getTavilyApiKey() {
        return tavilyApiKey;
    }

    public String getRapidApiKey() {
        return rapidApiKey;
    }

    public String getCloudflareApiToken() {
        return cloudflareApiToken;
    }

    public String getFirecrawlApiKey() {
        return firecrawlApiKey;
    }

    public String getFirecrawlUrl() {
        return firecrawlUrl;
    }

    public String getStripeSecretKey() {
        return stripeSecretKey;
    }

    public String getStripeWebhookSecret() {
        return stripeWebhookSecret;
    }

    public String getStripeDefaultPlanId() {
        return stripeDefaultPlanId;
    }

    public int getStripeDefaultTrialDays() {
        return stripeDefaultTrialDays;
    }

    public String getSandboxImageName() {
        return sandboxImageName;
    }

    public String getSandboxEntrypoint() {
        return sandboxEntrypoint;
    }
}
