package com.Nubian.ai.agentpress.service.openai;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.Nubian.ai.agentpress.model.LlmMessage;
import com.Nubian.ai.agentpress.model.LlmResponse;
import com.Nubian.ai.agentpress.service.billing.BillingServiceFacade;

import okhttp3.OkHttpClient;

/**
 * OpenAI-specific LLM service implementation.
 * This service focuses exclusively on OpenAI API interactions.
 */
@Service
public class OpenAIService {
    private static final Logger logger = LoggerFactory.getLogger(OpenAIService.class);
    
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final BillingServiceFacade billingServiceFacade;
    private final OpenAIRequestBuilder requestBuilder;
    private final OpenAIResponseParser responseParser;
    private final OpenAIStreamHandler streamHandler;

    @Autowired
    public OpenAIService(ObjectMapper objectMapper, BillingServiceFacade billingServiceFacade) {
        this.httpClient = new OkHttpClient();
        this.objectMapper = objectMapper;
        this.billingServiceFacade = billingServiceFacade;
        this.requestBuilder = new OpenAIRequestBuilder(objectMapper);
        this.responseParser = new OpenAIResponseParser(objectMapper);
        this.streamHandler = new OpenAIStreamHandler(objectMapper, billingServiceFacade);
        logger.info("Initialized OpenAI Service");
    }

    /**
     * Make an OpenAI API call
     */
    public CompletableFuture<LlmResponse> makeLlmApiCall(
            String apiKey,
            List<LlmMessage> messages,
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

        CompletableFuture<LlmResponse> futureResponse = new CompletableFuture<>();

        try {
            if (stream) {
                return streamHandler.handleStreamingRequest(
                    httpClient, apiKey, messages, modelName, temperature, maxTokens,
                    toolFunctionDefinitions, toolChoiceName, enableThinking, reasoningEffort,
                    userId, runId, startTime);
            } else {
                return requestBuilder.makeNonStreamingRequest(
                    httpClient, apiKey, messages, modelName, temperature, maxTokens,
                    toolFunctionDefinitions, toolChoiceName, enableThinking, reasoningEffort,
                    userId, runId, startTime, responseParser, billingServiceFacade);
            }

        } catch (Exception e) {
            logger.error("Error making OpenAI API call: {}", e.getMessage(), e);
            futureResponse.completeExceptionally(e);
            return futureResponse;
        }
    }
}
