package com.nubian.ai.agentpress.service.openai;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nubian.ai.agentpress.model.LlmResponse;
import com.nubian.ai.agentpress.model.ToolCall;

/**
 * Parses OpenAI API responses.
 */
public class OpenAIResponseParser {
    private static final Logger logger = LoggerFactory.getLogger(OpenAIResponseParser.class);

    private final ObjectMapper objectMapper;

    public OpenAIResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parse OpenAI response
     */
    public LlmResponse parseResponse(JsonNode jsonResponse, String modelName) {
        String id = jsonResponse.path("id").asText(UUID.randomUUID().toString());
        String model = jsonResponse.path("model").asText(modelName);
        
        JsonNode choices = jsonResponse.path("choices");
        String content = "";
        List<ToolCall> toolCalls = new ArrayList<>();
        String finishReason = "stop";
        
        if (choices.isArray() && choices.size() > 0) {
            JsonNode choice = choices.get(0);
            JsonNode message = choice.path("message");
            
            if (message.has("content") && !message.get("content").isNull()) {
                content = message.get("content").asText();
            }
            
            if (message.has("tool_calls")) {
                JsonNode toolCallsArray = message.get("tool_calls");
                for (JsonNode tc : toolCallsArray) {
                    String tcId = tc.path("id").asText();
                    String tcType = tc.path("type").asText("function");
                    JsonNode function = tc.path("function");
                    String functionName = function.path("name").asText();
                    String functionArgs = function.path("arguments").asText();
                    
                    toolCalls.add(new ToolCall(tcId, tcType, new ToolCall.FunctionCall(functionName, functionArgs)));
                }
            }
            
            finishReason = choice.path("finish_reason").asText("stop");
        }
        
        JsonNode usage = jsonResponse.path("usage");
        int promptTokens = usage.path("prompt_tokens").asInt(0);
        int completionTokens = usage.path("completion_tokens").asInt(0);
        int totalTokens = usage.path("total_tokens").asInt(promptTokens + completionTokens);
        
        return LlmResponse.builder()
            .setId(id)
            .setModel(model)
            .setContent(content)
            .setToolCalls(toolCalls.isEmpty() ? null : toolCalls)
            .setPromptTokens(promptTokens)
            .setCompletionTokens(completionTokens)
            .setTotalTokens(totalTokens)
            .setFinishReason(finishReason)
            .build();
    }

    /**
     * Process streaming chunk
     */
    public void processStreamChunk(JsonNode chunk, StringBuilder contentBuilder, List<ToolCall> toolCallsList) {
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
}
