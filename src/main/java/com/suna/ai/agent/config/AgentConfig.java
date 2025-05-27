package com.Nubian.ai.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for Agent implementation.
 */
@Configuration
@ConfigurationProperties(prefix = "agent")
@Data
public class AgentConfig {

    /**
     * Default model to use for agent runs.
     */
    private String defaultModel = "gemini-1.5-pro";
    
    /**
     * Maximum number of iterations for an agent run.
     */
    private int maxIterations = 10;
    
    /**
     * Timeout in seconds for agent runs.
     */
    private int timeoutSeconds = 300;
    
    /**
     * Model aliases to support cross-provider model references.
     */
    private Map<String, String> modelAliases = new HashMap<>();
    
    /**
     * Resolve a model name, possibly using an alias.
     * 
     * @param modelName The model name to resolve
     * @return The resolved model name
     */
    public String resolveModelName(String modelName) {
        if (modelName == null || modelName.isEmpty()) {
            return defaultModel;
        }
        
        return modelAliases.getOrDefault(modelName, modelName);
    }
}
