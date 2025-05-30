package com.nubian.ai.agentpress.service;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nubian.ai.agentpress.model.LlmMessage;
import com.nubian.ai.agentpress.model.LlmResponse;
import com.nubian.ai.agentpress.service.billing.BillingServiceFacade;
import org.springframework.beans.factory.annotation.Autowired;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Google LLM service implementation.
 * Handles communication with Google's Generative AI API.
 */
@Service
public class GoogleLlmService {
    private static final Logger logger = LoggerFactory.getLogger(GoogleLlmService.class);
    private static final MediaType JSON = MediaType.get("application/json");
    
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000; // 1 second
    
    private final String apiKey;
    private final OkHttpClient client;
    private final ObjectMapper objectMapper;
    private final BillingServiceFacade billingServiceFacade;
    
    private static final String API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    
    /**
     * Initialize the Google LLM service.
     */
    @Autowired
    public GoogleLlmService(
            @Value("${google.api.key:}") String apiKey,
            BillingServiceFacade billingServiceFacade,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.billingServiceFacade = billingServiceFacade;
        this.objectMapper = objectMapper;
        
        this.client = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofMinutes(5))
                .writeTimeout(Duration.ofMinutes(5))
                .build();
        
        logger.info("Initialized GoogleLlmService with API integration");
    }
    
    /**
     * Make an LLM API call to Google's Generative AI service.
     */
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
            Instant startTime) {
        
        // Map model name to Google's format
        String googleModelName = mapModelName(modelName);
        
        logger.info("Making Google API call using model: {}", googleModelName);
        logger.info("Parameters: temperature={}, max_tokens={}, tool_choice={}, stream={}",
                temperature, maxTokens, toolChoice, stream);
        
        CompletableFuture<LlmResponse> result = new CompletableFuture<>();
        
        // If API key is not configured, throw an exception
        if (apiKey == null || apiKey.isEmpty() || apiKey.equals("${google.api.key}")) {
            logger.error("Google API key not configured");
            CompletableFuture<LlmResponse> errorFuture = new CompletableFuture<>();
            errorFuture.completeExceptionally(new RuntimeException("Google API key not configured"));
            return errorFuture;
        }
        
        try {
            // Create request body
            ObjectNode requestBody = createRequestBody(
                messages, googleModelName, temperature, maxTokens,
                tools, toolChoice, stream, enableThinking, reasoningEffort
            );
            
            // Create HTTP request
            Request request = createApiRequest(requestBody, googleModelName);
            
            // Process the request synchronously with retries
            CompletableFuture.runAsync(() -> {
                int retries = 0;
                while (retries < MAX_RETRIES) {
                    try {
                        // Execute the request
                        try (Response response = client.newCall(request).execute()) {
                            if (!response.isSuccessful()) {
                                String errorBody = response.body() != null ? response.body().string() : "No response body";
                                logger.error("Google API call failed: {} - {}", response.code(), errorBody);
                                
                                // Complete exceptionally with the error
                                result.completeExceptionally(new RuntimeException("Google API call failed with status " + response.code() + ": " + errorBody));
                                return;
                            }
                            
                            // Parse the response
                            Instant endTime = Instant.now();
                            LlmResponse llmResponse = parseApiResponse(response, modelName);
                            
                            // Record LLM cost
                            if (userId != null && runId != null && startTime != null) {
                                billingServiceFacade.recordUsage(userId, runId, startTime, endTime, modelName);
                                logger.info("Recorded LLM usage for user {} run {}", userId, runId);
                            }
                            
                            result.complete(llmResponse);
                            return;
                        }
                    } catch (Exception e) {
                        logger.error("Error making Google API call: {}", e.getMessage(), e);
                        
                        if (retries < MAX_RETRIES - 1) {
                            retries++;
                            logger.warn("Retrying Google API call (attempt {}/{})", retries + 1, MAX_RETRIES);
                            try {
                                Thread.sleep(RETRY_DELAY_MS * retries);
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        } else {
                            // All retries failed, complete exceptionally
                            result.completeExceptionally(new RuntimeException("Failed to make Google API call after " + MAX_RETRIES + " retries: " + e.getMessage()));
                            return;
                        }
                    }
                }
                
                // If we get here, all retries failed
                result.completeExceptionally(new RuntimeException("Failed to make Google API call after " + MAX_RETRIES + " retries"));
            });
            
        } catch (Exception e) {
            logger.error("Failed to create API request: {}", e.getMessage(), e);
            result.completeExceptionally(new RuntimeException("Failed to create API request: " + e.getMessage()));
        }
        
        return result;
    }
    
    
    /**
     * Map model names to Google's format.
     */
    private String mapModelName(String modelName) {
        if (modelName == null) {
            return "gemini-1.5-pro";
        }
        
        // If it's already a Google model name, use it as is
        if (modelName.startsWith("gemini-")) {
            return modelName;
        }
        
        // Map OpenAI model names to Google model names
        switch (modelName.toLowerCase()) {
            case "gpt-4o":
            case "gpt-4-turbo":
                return "gemini-1.5-pro";
            case "gpt-4o-mini":
                return "gemini-1.5-flash";
            case "gpt-3.5-turbo":
                return "gemini-1.0-pro";
            default:
                return "gemini-1.5-pro"; // Default to Gemini 1.5 Pro
        }
    }
    
    /**
     * Create the request body for the Google API call.
     */
    private ObjectNode createRequestBody(
            List<LlmMessage> messages,
            String model,
            float temperature,
            Integer maxTokens,
            List<Map<String, Object>> tools,
            String toolChoice,
            boolean stream,
            boolean enableThinking,
            String reasoningEffort) {
        
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("temperature", temperature);
        
        if (maxTokens != null) {
            requestBody.put("maxOutputTokens", maxTokens);
        }
        
        // Convert LlmMessages to Google's format
        ArrayNode contents = requestBody.putArray("contents");
        
        // For Google, we need to combine all messages into a conversation
        // First, find the system message if any
        LlmMessage systemMessage = null;
        for (LlmMessage message : messages) {
            if ("system".equals(message.getRole())) {
                systemMessage = message;
                break;
            }
        }
        
        // Add user and assistant messages
        for (LlmMessage message : messages) {
            if ("system".equals(message.getRole())) {
                continue; // Skip system message for now
            }
            
            ObjectNode messageObj = contents.addObject();
            
            // Map OpenAI roles to Google roles
            String role = "user";
            if ("assistant".equals(message.getRole())) {
                role = "model";
            }
            
            messageObj.put("role", role);
            
            // Add parts
            ArrayNode parts = messageObj.putArray("parts");
            ObjectNode textPart = parts.addObject();
            
            if (message.getContent() instanceof String) {
                textPart.put("text", (String) message.getContent());
            } else {
                // Handle structured content (e.g., images)
                try {
                    textPart.put("text", objectMapper.writeValueAsString(message.getContent()));
                } catch (Exception e) {
                    textPart.put("text", "Error serializing content: " + e.getMessage());
                }
            }
            
            // If this is the first user message and we have a system message,
            // prepend the system message to the user message
            if ("user".equals(role) && systemMessage != null && message == messages.get(messages.indexOf(message))) {
                String systemPrefix = "System instructions: " + systemMessage.getContent() + "\n\nUser: ";
                textPart.put("text", systemPrefix + textPart.get("text").asText());
                systemMessage = null; // Use system message only once
            }
        }
        
        // Add tools if provided
        if (tools != null && !tools.isEmpty()) {
            ArrayNode toolsArray = requestBody.putArray("tools");
            for (Map<String, Object> tool : tools) {
                try {
                    // Convert OpenAPI tool format to Google tool format
                    ObjectNode toolNode = toolsArray.addObject();
                    toolNode.put("functionDeclarations", objectMapper.valueToTree(tool));
                } catch (Exception e) {
                    logger.warn("Failed to convert tool to Google format: {}", e.getMessage());
                }
            }
        }
        
        return requestBody;
    }
    
    /**
     * Create the HTTP request for the Google API call.
     */
    private Request createApiRequest(ObjectNode requestBody, String modelName) throws IOException {
        String requestBodyJson = objectMapper.writeValueAsString(requestBody);
        RequestBody body = RequestBody.create(requestBodyJson, JSON);
        
        // Build the URL for the specific model
        String apiUrl = API_BASE_URL + modelName + ":generateContent?key=" + apiKey;
        
        return new Request.Builder()
            .url(apiUrl)
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build();
    }
    
    /**
     * Parse the API response from Google.
     */
    private LlmResponse parseApiResponse(Response response, String model) throws IOException {
        String responseBody = response.body().string();
        JsonNode responseJson = objectMapper.readTree(responseBody);
        
        // Extract the content from the response
        String content = "";
        if (responseJson.has("candidates") && responseJson.get("candidates").size() > 0) {
            JsonNode firstCandidate = responseJson.get("candidates").get(0);
            
            if (firstCandidate.has("content") && 
                firstCandidate.get("content").has("parts") && 
                firstCandidate.get("content").get("parts").size() > 0) {
                
                JsonNode firstPart = firstCandidate.get("content").get("parts").get(0);
                if (firstPart.has("text")) {
                    content = firstPart.get("text").asText();
                }
            }
        }
        
        // Extract token usage (Google API might not provide this)
        int promptTokens = 0;
        int completionTokens = 0;
        int totalTokens = 0;
        
        if (responseJson.has("usageMetadata")) {
            JsonNode usage = responseJson.get("usageMetadata");
            promptTokens = usage.has("promptTokenCount") ? usage.get("promptTokenCount").asInt() : 0;
            completionTokens = usage.has("candidatesTokenCount") ? usage.get("candidatesTokenCount").asInt() : 0;
            totalTokens = promptTokens + completionTokens;
        }
        
        // Extract finish reason
        String finishReason = "stop"; // Default
        if (responseJson.has("candidates") && responseJson.get("candidates").size() > 0) {
            JsonNode firstCandidate = responseJson.get("candidates").get(0);
            if (firstCandidate.has("finishReason")) {
                finishReason = firstCandidate.get("finishReason").asText();
            }
        }
        
        // Build the response
        return LlmResponse.builder()
            .setId(responseJson.has("id") ? responseJson.get("id").asText() : UUID.randomUUID().toString())
            .setModel(model)
            .setContent(content)
            .setToolCalls(null) // Google API doesn't use OpenAI-style tool calls
            .setPromptTokens(promptTokens)
            .setCompletionTokens(completionTokens)
            .setTotalTokens(totalTokens)
            .setFinishReason(finishReason)
            .build();
    }
}
