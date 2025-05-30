package com.nubian.ai.agentpress.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.nubian.ai.agentpress.model.LlmMessage;
import com.nubian.ai.agentpress.model.LlmResponse;

/**
 * Service for making LLM API calls.
 * This is an abstract base class that defines the interface for LLM services.
 * Concrete implementations should extend this class and implement the makeLlmApiCall method.
 */
@Service
public abstract class LlmService {
    private static final Logger logger = LoggerFactory.getLogger(LlmService.class);
    
    /**
     * Make an LLM API call.
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
    public abstract CompletableFuture<LlmResponse> makeLlmApiCall(
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
            java.time.Instant startTime);
}
