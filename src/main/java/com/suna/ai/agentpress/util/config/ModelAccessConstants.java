package com.Nubian.ai.agentpress.util.config;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Constants for model access tiers.
 * This class defines which models are available to users on different subscription tiers.
 */
public class ModelAccessConstants {
    
    /**
     * Model access tiers mapping.
     * Maps subscription tier IDs to the set of models available on that tier.
     */
    public static final Map<String, Set<String>> MODEL_ACCESS_TIERS;
    
    static {
        Map<String, Set<String>> tiers = new HashMap<>();
        
        // Free tier models
        tiers.put("free", new HashSet<>(Arrays.asList(
            "openrouter/deepseek/deepseek-chat",
            "openrouter/qwen/qwen3-235b-a22b"
        )));
        
        // Tier 2 ($20/month) models
        tiers.put("tier_2_20", new HashSet<>(Arrays.asList(
            "openrouter/deepseek/deepseek-chat",
            "openai/gpt-4o",
            "anthropic/claude-3-7-sonnet-latest",
            "openrouter/qwen/qwen3-235b-a22b"
        )));
        
        // Tier 6 ($50/month) models
        tiers.put("tier_6_50", new HashSet<>(Arrays.asList(
            "openrouter/deepseek/deepseek-chat",
            "openai/gpt-4o",
            "anthropic/claude-3-7-sonnet-latest",
            "openrouter/qwen/qwen3-235b-a22b"
        )));
        
        // Tier 12 ($100/month) models
        tiers.put("tier_12_100", new HashSet<>(Arrays.asList(
            "openrouter/deepseek/deepseek-chat",
            "openai/gpt-4o",
            "anthropic/claude-3-7-sonnet-latest",
            "openrouter/qwen/qwen3-235b-a22b"
        )));
        
        // Tier 25 ($200/month) models
        tiers.put("tier_25_200", new HashSet<>(Arrays.asList(
            "openrouter/deepseek/deepseek-chat",
            "openai/gpt-4o",
            "anthropic/claude-3-7-sonnet-latest",
            "openrouter/qwen/qwen3-235b-a22b"
        )));
        
        // Tier 50 ($400/month) models
        tiers.put("tier_50_400", new HashSet<>(Arrays.asList(
            "openrouter/deepseek/deepseek-chat",
            "openai/gpt-4o",
            "anthropic/claude-3-7-sonnet-latest",
            "openrouter/qwen/qwen3-235b-a22b"
        )));
        
        // Tier 125 ($800/month) models
        tiers.put("tier_125_800", new HashSet<>(Arrays.asList(
            "openrouter/deepseek/deepseek-chat",
            "openai/gpt-4o",
            "anthropic/claude-3-7-sonnet-latest",
            "openrouter/qwen/qwen3-235b-a22b"
        )));
        
        // Tier 200 ($1000/month) models
        tiers.put("tier_200_1000", new HashSet<>(Arrays.asList(
            "openrouter/deepseek/deepseek-chat",
            "openai/gpt-4o",
            "anthropic/claude-3-7-sonnet-latest",
            "openrouter/qwen/qwen3-235b-a22b"
        )));
        
        MODEL_ACCESS_TIERS = Collections.unmodifiableMap(tiers);
    }
    
    /**
     * Check if a model is available for a specific tier.
     *
     * @param tierName The name of the tier
     * @param modelName The name of the model to check
     * @return true if the model is available for the tier, false otherwise
     */
    public static boolean isModelAvailableForTier(String tierName, String modelName) {
        Set<String> availableModels = MODEL_ACCESS_TIERS.get(tierName);
        return availableModels != null && availableModels.contains(modelName);
    }
    
    /**
     * Get all models available for a specific tier.
     *
     * @param tierName The name of the tier
     * @return A set of model names available for the tier, or an empty set if the tier doesn't exist
     */
    public static Set<String> getModelsForTier(String tierName) {
        Set<String> availableModels = MODEL_ACCESS_TIERS.get(tierName);
        return availableModels != null ? availableModels : Collections.emptySet();
    }
    
    // Private constructor to prevent instantiation
    private ModelAccessConstants() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
