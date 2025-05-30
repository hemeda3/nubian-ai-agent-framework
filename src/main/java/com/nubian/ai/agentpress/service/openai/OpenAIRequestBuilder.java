package com.nubian.ai.agentpress.service.openai;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nubian.ai.agentpress.model.LlmMessage;
import com.nubian.ai.agentpress.model.LlmResponse;
import com.nubian.ai.agentpress.model.ToolCall;
import com.nubian.ai.agentpress.service.billing.BillingServiceFacade;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Builds OpenAI API requests and handles non-streaming responses.
 */
public class OpenAIRequestBuilder {
    private static final Logger logger = LoggerFactory.getLogger(OpenAIRequestBuilder.class);
    private static final String OPENAI_BASE_URL = "https://api.openai.com/v1";
    private static final String CHAT_ENDPOINT = "/chat/completions";

    private final ObjectMapper objectMapper;

    public OpenAIRequestBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Build OpenAI request body
     */
    public Map<String, Object> buildRequestBody(
            List<LlmMessage> messages,
            String modelName,
            float temperature,
            Integer maxTokens,
            List<Map<String, Object>> toolFunctionDefinitions,
            String toolChoiceName,
            boolean stream,
            boolean enableThinking,
            String reasoningEffort) {
        
        Map<String, Object> requestBody = new HashMap<>();
        
        requestBody.put("model", modelName != null ? modelName : "gpt-3.5-turbo");
        requestBody.put("messages", convertMessages(messages));
        requestBody.put("temperature", temperature);
        requestBody.put("stream", stream);
        
        if (maxTokens != null) {
            requestBody.put("max_tokens", maxTokens);
        }
        
        if (toolFunctionDefinitions != null && !toolFunctionDefinitions.isEmpty()) {
            List<Map<String, Object>> tools = new ArrayList<>();
            for (int i = 0; i < toolFunctionDefinitions.size(); i++) {
                Map<String, Object> toolDefInput = toolFunctionDefinitions.get(i);

                if (toolDefInput == null) {
                    logger.error("OpenAIRequestBuilder: toolDefInput at index {} is null. Skipping.", i);
                    continue; 
                }

                // Ensure toolDef is mutable and has a valid 'name'
                Map<String, Object> toolDef = new HashMap<>(toolDefInput); // Make a mutable copy
                Object nameObj = null;
                try {
                    nameObj = toolDef.get("name");
                } catch (Exception e) {
                    logger.error("OpenAIRequestBuilder: Exception getting 'name' from toolDef at index {}. toolDef: {}. Error: {}", i, toolDef, e.getMessage());
                    // Fall through to nameStr == null logic
                }
                
                String nameStr = null;
                if (nameObj instanceof String) {
                    nameStr = ((String) nameObj).trim();
                }

                if (nameStr == null || nameStr.isEmpty() || !nameStr.matches("^[a-zA-Z0-9_-]{1,64}$")) {
                    String originalName = nameStr; // Could be null or invalid
                    String generatedName = "fallback_tool_" + java.util.UUID.randomUUID().toString().substring(0, 8).replace("-", "");
                    
                    if (!generatedName.matches("^[a-zA-Z0-9_-]{1,64}$")) { // Should not happen with this generation
                        logger.error("CRITICAL_RB: Generated fallback name '{}' is invalid. Defaulting to 'ultra_fallback_tool'", generatedName);
                        generatedName = "ultra_fallback_tool";
                    }
                    nameStr = generatedName; // nameStr is now the valid, generated name
                    logger.error("OpenAIRequestBuilder: toolDef 'name' at index {} was missing, empty, or invalid (was: '{}'). Using fallback: {}. Original toolDef: {}", i, originalName, nameStr, toolDefInput);
                    toolDef.put("name", nameStr); // Put the guaranteed valid name into toolDef

                    if (toolDef.get("description") == null || toolDef.get("description").toString().trim().isEmpty()) {
                        toolDef.put("description", "Fallback description for " + nameStr);
                    }
                    if (!toolDef.containsKey("parameters") || !(toolDef.get("parameters") instanceof Map)) {
                         toolDef.put("parameters", Map.of("type", "object", "properties", new HashMap<>()));
                    }
                } else {
                    // Name was valid, ensure the (potentially trimmed) nameStr is what's in toolDef
                    toolDef.put("name", nameStr); 
                }
                
                Map<String, Object> tool = new HashMap<>();
                tool.put("type", "function");
                tool.put("function", toolDef); 
                tools.add(tool);
            }
            requestBody.put("tools", tools);
            
            if (toolChoiceName != null && !toolChoiceName.isEmpty()) {
                if ("auto".equalsIgnoreCase(toolChoiceName) || "none".equalsIgnoreCase(toolChoiceName)) {
                    requestBody.put("tool_choice", toolChoiceName);
                } else {
                    Map<String, Object> toolChoice = new HashMap<>();
                    toolChoice.put("type", "function");
                    Map<String, Object> function = new HashMap<>();
                    function.put("name", toolChoiceName);
                    toolChoice.put("function", function);
                    requestBody.put("tool_choice", toolChoice);
                }
            }
        }
        
        // Add thinking mode for supported models (o1 series)
        if (enableThinking && modelName != null && modelName.startsWith("o1")) {
            if (reasoningEffort != null) {
                requestBody.put("reasoning_effort", reasoningEffort);
            }
        }
        
        return requestBody;
    }

    /**
     * Convert messages to OpenAI format
     */
    private List<Map<String, Object>> convertMessages(List<LlmMessage> messages) {
        List<Map<String, Object>> openAIMessages = new ArrayList<>();
        
        for (LlmMessage message : messages) {
            Map<String, Object> openAIMessage = new HashMap<>();
            openAIMessage.put("role", message.getRole());
            
            if (message.getContent() != null) {
                openAIMessage.put("content", message.getContent().toString());
            }
            
            if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
                List<Map<String, Object>> toolCalls = new ArrayList<>();
                for (ToolCall toolCall : message.getToolCalls()) {
                    Map<String, Object> tc = new HashMap<>();
                    tc.put("id", toolCall.getId());
                    tc.put("type", toolCall.getType());
                    Map<String, Object> function = new HashMap<>();
                    function.put("name", toolCall.getFunction().getName());
                    function.put("arguments", toolCall.getFunction().getArguments());
                    tc.put("function", function);
                    toolCalls.add(tc);
                }
                openAIMessage.put("tool_calls", toolCalls);
            }
            
            if (message.getName() != null) {
                openAIMessage.put("name", message.getName());
            }
            
            if (message.getToolCallId() != null) {
                openAIMessage.put("tool_call_id", message.getToolCallId());
            }
            
            openAIMessages.add(openAIMessage);
        }
        
        return openAIMessages;
    }

    /**
     * Make non-streaming request
     */
    public CompletableFuture<LlmResponse> makeNonStreamingRequest(
            OkHttpClient httpClient,
            String apiKey,
            List<LlmMessage> messages,
            String modelName,
            float temperature,
            Integer maxTokens,
            List<Map<String, Object>> toolFunctionDefinitions,
            String toolChoiceName,
            boolean enableThinking,
            String reasoningEffort,
            String userId,
            String runId,
            Instant startTime,
            OpenAIResponseParser responseParser,
            BillingServiceFacade billingServiceFacade) {

        CompletableFuture<LlmResponse> futureResponse = new CompletableFuture<>();

        try {
            Map<String, Object> requestBody = buildRequestBody(
                messages, modelName, temperature, maxTokens,
                toolFunctionDefinitions, toolChoiceName, false,
                enableThinking, reasoningEffort
            );

            String url = OPENAI_BASE_URL + CHAT_ENDPOINT;
            
            Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + apiKey);

            String jsonBody = objectMapper.writeValueAsString(requestBody);
            // Log the exact JSON body being sent, especially the tools part
            if (logger.isTraceEnabled()) { // Or DEBUG if preferred, TRACE can be very verbose
                logger.trace("OpenAI Request JSON Body: {}", jsonBody);
            } else if (jsonBody.contains("fallback_tool_") || jsonBody.contains("ultra_fallback_tool")) {
                // Log if fallback names were used, as it's a sign of issues upstream
                logger.warn("OpenAI Request JSON Body with fallback names: {}", jsonBody);
            }


            RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json"));
            requestBuilder.post(body);

            httpClient.newCall(requestBuilder.build()).enqueue(new okhttp3.Callback() {
                @Override
                public void onResponse(okhttp3.Call call, Response response) {
                    try (Response res = response) {
                        if (!res.isSuccessful()) {
                            futureResponse.completeExceptionally(
                                new RuntimeException("HTTP " + res.code() + ": " + res.message())
                            );
                            return;
                        }

                        String responseBody = res.body().string();
                        JsonNode jsonResponse = objectMapper.readTree(responseBody);
                        
                        LlmResponse llmResponse = responseParser.parseResponse(jsonResponse, modelName);
                        futureResponse.complete(llmResponse);
                        
                        // Record billing
                        recordBilling(billingServiceFacade, userId, runId, startTime, modelName, 
                            llmResponse.getPromptTokens(), llmResponse.getCompletionTokens());
                        
                    } catch (Exception e) {
                        logger.error("Error processing response: {}", e.getMessage(), e);
                        futureResponse.completeExceptionally(e);
                    }
                }

                @Override
                public void onFailure(okhttp3.Call call, java.io.IOException e) {
                    logger.error("Request failed: {}", e.getMessage(), e);
                    futureResponse.completeExceptionally(e);
                }
            });

        } catch (Exception e) {
            logger.error("Error building request: {}", e.getMessage(), e);
            futureResponse.completeExceptionally(e);
        }

        return futureResponse;
    }

    private void recordBilling(BillingServiceFacade billingServiceFacade, String userId, String runId, 
                              Instant startTime, String modelName, int promptTokens, int completionTokens) {
        try {
            // Implementation depends on your billing service interface
            logger.debug("Recording billing for user: {}, run: {}, model: {}, prompt tokens: {}, completion tokens: {}", 
                userId, runId, modelName, promptTokens, completionTokens);
        } catch (Exception e) {
            logger.error("Error recording billing: {}", e.getMessage(), e);
        }
    }
}
