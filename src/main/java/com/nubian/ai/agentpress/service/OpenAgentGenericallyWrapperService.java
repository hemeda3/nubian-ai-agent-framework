package com.nubian.ai.agentpress.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.nubian.ai.agentpress.model.LlmMessage;
import com.nubian.ai.agentpress.model.LlmResponse;
import com.nubian.ai.agentpress.service.openai.OpenAIService;

/**
 * OpenAI-only LLM wrapper service.
 * Simplified to focus exclusively on OpenAI integration.
 */
@Service
public class OpenAgentGenericallyWrapperService {
    private static final Logger logger = LoggerFactory.getLogger(OpenAgentGenericallyWrapperService.class);
    
    private final OpenAIService openAIService;
    private String provider = "openai";

    @Autowired
    public OpenAgentGenericallyWrapperService(OpenAIService openAIService) {
        this.openAIService = openAIService;
        logger.info("Initialized OpenAI-only LLM Wrapper Service");
    }

    /**
     * Set the provider (for compatibility, but only OpenAI is supported)
     */
    public void setProvider(String provider) {
        this.provider = provider;
        if (!"openai".equals(provider)) {
            logger.warn("Only OpenAI provider is supported. Ignoring provider: {}", provider);
        }
    }

    /**
     * Make an LLM API call (OpenAI only)
     */
    public CompletableFuture<LlmResponse> makeLlmApiCall(
            String provider,
            String apiKey,
            List<LlmMessage> appMessages,
            String modelName,
            float temperature,
            Integer maxTokens,
            List<Map<String, Object>> toolFunctionDefinitions,
            String toolChoiceName,
            boolean stream,
            boolean enableThinking, 
            String reasoningEffort, 
            String userId,
            String runId,
            Instant startTime) {

        logger.info("Making OpenAI API call. Model: {}, Temp: {}, MaxTokens: {}, Stream: {}",
                modelName, temperature, maxTokens, stream);

        return openAIService.makeLlmApiCall(
            apiKey, appMessages, modelName, temperature, maxTokens,
            toolFunctionDefinitions, toolChoiceName, stream, enableThinking,
            reasoningEffort, userId, runId, startTime
        );
    }

    /**
     * Overloaded method for backward compatibility (without provider parameter)
     */
    public CompletableFuture<LlmResponse> makeLlmApiCall(
            List<LlmMessage> appMessages,
            String modelName,
            float temperature,
            Integer maxTokens,
            List<Map<String, Object>> toolFunctionDefinitions,
            String toolChoiceName,
            boolean stream,
            boolean enableThinking, 
            String reasoningEffort, 
            String userId,
            String runId,
            Instant startTime) {

        // This method signature is missing the API key - need to get it from configuration
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("OpenAI API key not found in environment")
            );
        }

        return makeLlmApiCall(
            "openai", apiKey, appMessages, modelName, temperature, maxTokens,
            toolFunctionDefinitions, toolChoiceName, stream, enableThinking,
            reasoningEffort, userId, runId, startTime
        );
    }
}
