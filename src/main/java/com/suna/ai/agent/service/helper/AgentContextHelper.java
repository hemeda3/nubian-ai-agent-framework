package com.Nubian.ai.agent.service.helper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.Nubian.ai.agentpress.model.LlmMessage;
import com.Nubian.ai.agentpress.model.Message;
import com.Nubian.ai.agentpress.service.ContextManager;
import com.Nubian.ai.agentpress.service.DBConnection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/**
 * Helper class for context management in agent runs.
 * Handles context summarization and temporary message construction.
 */
@Slf4j
@RequiredArgsConstructor
public class AgentContextHelper {
    private final ContextManager contextManager;
    private final DBConnection dbConnection;
    private final ObjectMapper objectMapper;
    
    /**
     * Check and summarize context if needed.
     *
     * @param threadId The thread ID
     * @param modelName The model name
     * @param enableContextManager Whether to enable context management
     * @param userId The ID of the user initiating the summarization
     * @param runId The ID of the agent run (if applicable)
     * @param startTime The start time of the LLM call for billing purposes
     * @return A CompletableFuture that completes with true if summarization was performed
     */
    public CompletableFuture<Boolean> checkAndSummarizeContext(
            String threadId, 
            String modelName, 
            boolean enableContextManager,
            String userId,
            String runId,
            java.time.Instant startTime) {
            
        if (!enableContextManager) {
            log.info("Context management disabled for thread {}", threadId);
            return CompletableFuture.completedFuture(false);
        }
        
        log.info("Checking context window for thread {}", threadId);
        
        // Callback for adding summary message
        BiFunction<String, LlmMessage, CompletableFuture<Message>> summaryAddCallback =
            (tId, summaryLlmMsg) -> dbConnection.insertMessage(
                new Message(
                    tId, 
                    "summary", 
                    summaryLlmMsg.getContent(), 
                    true,
                    Map.of(
                        "token_count", contextManager.getThreadTokenCount(tId).join(), // This is the token_count_before_summary
                        "model_used", modelName != null ? modelName : "default",
                        "summary_timestamp", System.currentTimeMillis()
                    )
                )
            );
        
        return contextManager.checkAndSummarizeIfNeeded(
            threadId, 
            summaryAddCallback, 
            modelName, 
            false, // Don't force, let threshold decide
            userId,
            runId,
            startTime
        ).thenApply(summarized -> {
            if (summarized) {
                log.info("Context summarized for thread {}", threadId);
            }
            return summarized;
        });
    }
    
    /**
     * Construct a temporary message with visual context (browser state, image context).
     *
     * @param threadId The thread ID
     * @return A CompletableFuture that completes with the temporary message, or null if no context
     */
    public CompletableFuture<LlmMessage> constructTemporaryMessage(String threadId) {
        List<CompletableFuture<List<Map<String, Object>>>> futures = new ArrayList<>();
        
        // Get latest browser state
        futures.add(dbConnection.queryForList(
            "SELECT * FROM messages WHERE thread_id = ? AND type = ? ORDER BY created_at DESC LIMIT 1", 
            threadId, "browser_state"
        ));
        
        // Get latest image context
        futures.add(dbConnection.queryForList(
            "SELECT * FROM messages WHERE thread_id = ? AND type = ? ORDER BY created_at DESC LIMIT 1", 
            threadId, "image_context"
        ));
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                try {
                    List<Map<String, Object>> tempMessageContentList = new ArrayList<>();
                    
                    // Process browser state
                    List<Map<String, Object>> browserStateResults = futures.get(0).join();
                    if (!browserStateResults.isEmpty()) {
                        Map<String, Object> browserMsg = browserStateResults.get(0);
                        Map<String, Object> browserContent = parseContent(browserMsg.get("content"));
                        
                        String screenshotUrl = (String) browserContent.get("screenshot_url");
                        if (screenshotUrl == null) {
                            screenshotUrl = (String) browserContent.get("image_url");
                        }
                        
                        // Create a copy without screenshot data for text content
                        Map<String, Object> browserStateTextMap = new HashMap<>(browserContent);
                        browserStateTextMap.remove("screenshot_base64");
                        browserStateTextMap.remove("screenshot_url");
                        browserStateTextMap.remove("image_url");
                        
                        if (!browserStateTextMap.isEmpty()) {
                            try {
                                tempMessageContentList.add(Map.of(
                                    "type", "text", 
                                    "text", "Current browser state:\n" + objectMapper.writeValueAsString(browserStateTextMap)
                                ));
                            } catch (JsonProcessingException e) {
                                log.error("Error serializing browser state: {}", e.getMessage(), e);
                            }
                        }
                        
                        if (screenshotUrl != null) {
                            tempMessageContentList.add(Map.of(
                                "type", "image_url", 
                                "image_url", Map.of("url", screenshotUrl)
                            ));
                        } else if (browserContent.get("screenshot_base64") != null) {
                            tempMessageContentList.add(Map.of(
                                "type", "image_url", 
                                "image_url", Map.of(
                                    "url", "data:image/jpeg;base64," + browserContent.get("screenshot_base64")
                                )
                            ));
                        }
                    }
                    
                    // Process image context
                    List<Map<String, Object>> imageContextResults = futures.get(1).join();
                    if (!imageContextResults.isEmpty()) {
                        Map<String, Object> imageCtxMsg = imageContextResults.get(0);
                        Map<String, Object> imageCtxContent = parseContent(imageCtxMsg.get("content"));
                        
                        String base64Image = (String) imageCtxContent.get("base64");
                        String mimeType = (String) imageCtxContent.get("mime_type");
                        String filePath = (String) imageCtxContent.get("file_path");
                        
                        if (base64Image != null && mimeType != null) {
                            tempMessageContentList.add(Map.of(
                                "type", "text", 
                                "text", "Image context for '" + filePath + "':"
                            ));
                            
                            tempMessageContentList.add(Map.of(
                                "type", "image_url", 
                                "image_url", Map.of(
                                    "url", "data:" + mimeType + ";base64," + base64Image
                                )
                            ));
                        }
                    }
                    
                    if (tempMessageContentList.isEmpty()) {
                        return null;
                    }
                    
                    // Delete old image context messages after they've been used
                    dbConnection.deleteMessagesByType(threadId, "image_context");
                    
                    // Create temporary user message with the content
                    LlmMessage temporaryUserMessage = new LlmMessage("user", null);
                    
                    // Simplified approach for now: if only text, use it directly
                    // For multi-modal content, set the list directly as content
                    // LlmMessage constructor can accept List<Object> for multi-modal content
                    temporaryUserMessage.setContent(tempMessageContentList);
                    
                    return temporaryUserMessage;
                } catch (Exception e) {
                    log.error("Error constructing temporary message: {}", e.getMessage(), e);
                    return null;
                }
            });
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseContent(Object content) {
        if (content instanceof String) {
            try {
                return objectMapper.readValue((String) content, Map.class);
            } catch (JsonProcessingException e) {
                log.error("Error parsing content: {}", e.getMessage(), e);
                return new HashMap<>();
            }
        } else if (content instanceof Map) {
            return (Map<String, Object>) content;
        } else {
            return new HashMap<>();
        }
    }
}
