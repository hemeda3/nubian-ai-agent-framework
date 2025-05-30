package com.nubian.ai.agentpress.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.nubian.ai.agentpress.model.LlmMessage;
import com.nubian.ai.agentpress.model.LlmResponse;

/**
 * Default implementation of the LLM service.
 * This implementation delegates to the OpenAILlmService.
 */
@Service
public class DefaultLlmService extends LlmService {
    private static final Logger logger = LoggerFactory.getLogger(DefaultLlmService.class);
    
    private final OpenAgentGenericallyWrapperService openAgentGenricllmWrapperService;
    
    /**
     * Initialize the default LLM service.
     * 
     * @param openAgentGenricllmWrapperService The OpenAI LLM service to delegate to
     */
    @Autowired
    public DefaultLlmService(OpenAgentGenericallyWrapperService openAgentGenricllmWrapperService) {
        this.openAgentGenricllmWrapperService = openAgentGenricllmWrapperService;
        logger.debug("Initialized DefaultLlmService");
    }
    
    /**
     * Make an LLM API call by delegating to the OpenAILlmService.
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
        
        logger.debug("Delegating LLM API call to OpenAILlmService");
        
        return openAgentGenricllmWrapperService.makeLlmApiCall(
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
    }
}
