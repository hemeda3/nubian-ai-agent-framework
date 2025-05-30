package com.nubian.ai.agentpress.service.openai;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;

/**
 * Handles OpenAI streaming responses.
 */
public class OpenAIStreamHandler {
    private static final Logger logger = LoggerFactory.getLogger(OpenAIStreamHandler.class);
    private static final String OPENAI_BASE_URL = "https://api.openai.com/v1";
    private static final String CHAT_ENDPOINT = "/chat/completions";

    private final ObjectMapper objectMapper;
    private final BillingServiceFacade billingServiceFacade;

    public OpenAIStreamHandler(ObjectMapper objectMapper, BillingServiceFacade billingServiceFacade) {
        this.objectMapper = objectMapper;
        this.billingServiceFacade = billingServiceFacade;
    }

    /**
     * Handle streaming request
     */
    public CompletableFuture<LlmResponse> handleStreamingRequest(
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
            Instant startTime) {

        CompletableFuture<LlmResponse> futureResponse = new CompletableFuture<>();

        try {
            OpenAIRequestBuilder requestBuilder = new OpenAIRequestBuilder(objectMapper);
            Map<String, Object> requestBody = requestBuilder.buildRequestBody(
                messages, modelName, temperature, maxTokens,
                toolFunctionDefinitions, toolChoiceName, true,
                enableThinking, reasoningEffort
            );

            String url = OPENAI_BASE_URL + CHAT_ENDPOINT;
            
            Request.Builder reqBuilder = new Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + apiKey);

            String jsonBody = objectMapper.writeValueAsString(requestBody);
            RequestBody body = RequestBody.create(jsonBody, MediaType.get("application/json"));
            reqBuilder.post(body);

            EventSourceListener listener = new EventSourceListener() {
                private final StringBuilder contentBuilder = new StringBuilder();
                private final List<ToolCall> toolCallsList = new ArrayList<>();
                private String responseId = UUID.randomUUID().toString();
                private int promptTokens = 0;
                private int completionTokens = 0;
                private String finishReason = "stop";

                @Override
                public void onEvent(EventSource eventSource, String id, String type, String data) {
                    if ("data".equals(type) && !"[DONE]".equals(data)) {
                        try {
                            JsonNode chunk = objectMapper.readTree(data);
                            processStreamChunk(chunk, contentBuilder, toolCallsList);
                            
                            // Extract usage information if available
                            if (chunk.has("usage")) {
                                JsonNode usage = chunk.get("usage");
                                promptTokens = usage.path("prompt_tokens").asInt();
                                completionTokens = usage.path("completion_tokens").asInt();
                            }
                            
                            // Extract finish reason
                            JsonNode choices = chunk.path("choices");
                            if (choices.isArray() && choices.size() > 0) {
                                JsonNode choice = choices.get(0);
                                if (choice.has("finish_reason") && !choice.get("finish_reason").isNull()) {
                                    finishReason = choice.get("finish_reason").asText();
                                }
                            }
                            
                        } catch (JsonProcessingException e) {
                            logger.error("Error processing stream chunk: {}", e.getMessage());
                        }
                    }
                }

                @Override
                public void onClosed(EventSource eventSource) {
                    LlmResponse response = LlmResponse.builder()
                        .setId(responseId)
                        .setModel(modelName)
                        .setContent(contentBuilder.toString())
                        .setToolCalls(toolCallsList.isEmpty() ? null : toolCallsList)
                        .setPromptTokens(promptTokens)
                        .setCompletionTokens(completionTokens)
                        .setTotalTokens(promptTokens + completionTokens)
                        .setFinishReason(finishReason)
                        .build();

                    futureResponse.complete(response);
                    recordBilling(userId, runId, startTime, modelName, promptTokens, completionTokens);
                }

                @Override
                public void onFailure(EventSource eventSource, Throwable t, Response response) {
                    String errorMessage = (t != null) ? t.getMessage() : "Unknown error";
                    String responseBodyString = null;
                    if (response != null) {
                        errorMessage += " (HTTP " + response.code() + ")";
                        try {
                            if (response.body() != null) {
                                responseBodyString = response.body().string(); // Read the body
                                logger.error("OpenAI stream failure response body: {}", responseBodyString);
                            } else {
                                logger.error("OpenAI stream failure response body is null.");
                            }
                        } catch (Exception e) {
                            logger.error("Error reading OpenAI stream failure response body: {}", e.getMessage(), e);
                        }
                    } else {
                         logger.error("OpenAI stream failure, response object is null.");
                    }

                    String finalErrorMessage = "OpenAI stream failed: " + errorMessage;
                    if (responseBodyString != null && !responseBodyString.isEmpty()) {
                        finalErrorMessage += " - Body: " + responseBodyString;
                    }
                    
                    logger.error(finalErrorMessage, t); // Pass t as the throwable for full stack trace
                    
                    // Ensure we complete exceptionally with a non-null throwable
                    if (t != null) {
                        futureResponse.completeExceptionally(new RuntimeException(finalErrorMessage, t));
                    } else {
                        futureResponse.completeExceptionally(new RuntimeException(finalErrorMessage));
                    }
                }
            };

            EventSource eventSource = EventSources.createFactory(httpClient).newEventSource(reqBuilder.build(), listener);

        } catch (Exception e) {
            logger.error("Error setting up stream: {}", e.getMessage(), e);
            futureResponse.completeExceptionally(e);
        }

        return futureResponse;
    }

    private void processStreamChunk(JsonNode chunk, StringBuilder contentBuilder, List<ToolCall> toolCallsList) {
        JsonNode choices = chunk.path("choices");
        if (choices.isArray() && choices.size() > 0) {
            JsonNode choice = choices.get(0);
            JsonNode delta = choice.path("delta");
            
            if (delta.has("content") && !delta.get("content").isNull()) {
                contentBuilder.append(delta.get("content").asText());
            }
            
            if (delta.has("tool_calls")) {
                JsonNode toolCalls = delta.get("tool_calls");
                // TODO: Process tool calls in streaming context
                logger.debug("Processing tool calls in stream: {}", toolCalls);
            }
        }
    }

    private void recordBilling(String userId, String runId, Instant startTime, String modelName, 
                              int promptTokens, int completionTokens) {
        try {
            logger.debug("Recording billing for user: {}, run: {}, model: {}, prompt tokens: {}, completion tokens: {}", 
                userId, runId, modelName, promptTokens, completionTokens);
        } catch (Exception e) {
            logger.error("Error recording billing: {}", e.getMessage(), e);
        }
    }
}
