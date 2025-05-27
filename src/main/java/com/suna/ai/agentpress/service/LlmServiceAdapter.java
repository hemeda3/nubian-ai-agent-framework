package com.Nubian.ai.agentpress.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.Nubian.ai.agentpress.model.LlmMessage;
import com.Nubian.ai.agentpress.model.LlmResponse;

/**
 * Adapter for LLM services to implement the LlmService interface.
 * This allows different LLM services to be used interchangeably.
 */
public class LlmServiceAdapter extends LlmService {
    private static final Logger logger = LoggerFactory.getLogger(LlmServiceAdapter.class);
    
    private final Object llmService; // Can be either OpenAILlmService or GoogleLlmService
    private final String serviceType;
    
    /**
     * Initialize the LLM service adapter for OpenAI.
     * 
     * @param openAgentGenricllmWrapperService The OpenAI LLM service to adapt
     */
    public LlmServiceAdapter(OpenAgentGenericallyWrapperService openAgentGenricllmWrapperService) {
        this.llmService = openAgentGenricllmWrapperService;
        this.serviceType = "openai";
        logger.debug("Initialized LLM service adapter for OpenAI");
    }
    
    /**
     * Initialize the LLM service adapter for Google.
     * 
     * @param googleLlmService The Google LLM service to adapt
     */
    public LlmServiceAdapter(GoogleLlmService googleLlmService) {
        this.llmService = googleLlmService;
        this.serviceType = "google";
        logger.debug("Initialized LLM service adapter for Google");
    }
    
    /**
     * Make an LLM API call using the adapted OpenAI service.
     * 
     * @param messages The messages to send to the LLM
     * @param modelName The name of the LLM model to use
     * @param temperature The temperature parameter for response randomness (0-1)
     * @param maxTokens The maximum number of tokens in the LLM response
     * @param tools The tools available to the LLM
     * @param toolChoice The tool choice preference ("auto", "required", "none")
     * @param stream Whether to use streaming API for the LLM response
     * @param enableThinking Whether to enable thinking before making a decision
     * @param reasoningEffort The effort level for reasoning
     * @return A CompletableFuture that completes with the LLM response
     */
    @Override
    public CompletableFuture<LlmResponse> makeLlmApiCall(
            List<LlmMessage> messages,
            String modelName,
            float temperature,
            Integer maxTokens,
            List<Map<String, Object>> tools,
            String toolChoice,
            boolean stream,
            boolean enableThinking,
            String reasoningEffort,
            String userId,
            String runId,
            java.time.Instant startTime) {
        
        // Delegate to the appropriate service based on serviceType
        if ("google".equals(serviceType)) {
            GoogleLlmService googleLlmService = (GoogleLlmService) llmService;
            return googleLlmService.makeLlmApiCall(
                    messages,
                    modelName,
                    temperature,
                    maxTokens,
                    tools,
                    toolChoice,
                    stream,
                    enableThinking,
                    reasoningEffort,
                    userId,
                    runId,
                    startTime
            );
        } else {
            // Default to OpenAI
            OpenAgentGenericallyWrapperService openAgentGenricllmWrapperService = (OpenAgentGenericallyWrapperService) llmService;
            
            // Map model names if needed
            String mappedModelName = mapModelName(modelName);
            
            return openAgentGenricllmWrapperService.makeLlmApiCall(
                    messages,
                    mappedModelName,
                    temperature,
                    maxTokens,
                    tools,
                    toolChoice,
                    stream,
                    enableThinking,
                    reasoningEffort,
                    userId,
                    runId,
                    startTime
            );
        }
    }
    
    /**
     * Log which LLM service is being used.
     */
    private void logServiceType() {
        logger.info("Using LLM service type: {}", serviceType);
        logger.info("Service implementation: {}", llmService.getClass().getSimpleName());
    }
    
    /**
     * Map Google model names to OpenAI model names.
     * 
     * @param modelName The Google model name
     * @return The corresponding OpenAI model name
     */
    private String mapModelName(String modelName) {
        if (modelName == null) {
            return "gpt-4o";
        }
        
        // Map Google model names to OpenAI model names
        switch (modelName.toLowerCase()) {
            case "gemini-1.5-pro":
            case "gemini-pro":
                return "gpt-4o";
            case "gemini-1.5-flash":
            case "gemini-flash":
                return "gpt-4o-mini";
            case "gemini-1.0-pro":
                return "gpt-4";
            case "gemini-1.0-ultra":
                return "gpt-4-turbo";
            default:
                // If it's already an OpenAI model name, use it as is
                if (modelName.startsWith("gpt-")) {
                    return modelName;
                }
                // Default to GPT-4o
                return "gpt-4o";
        }
    }
}
