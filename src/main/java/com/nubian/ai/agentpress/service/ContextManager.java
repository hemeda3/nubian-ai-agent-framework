package com.nubian.ai.agentpress.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nubian.ai.agentpress.model.LlmMessage;
import com.nubian.ai.agentpress.model.Message;
import com.nubian.ai.agentpress.model.ToolCall; // Added import for ToolCall

/**
 * Manages thread context including token counting and summarization.
 */
@Service
public class ContextManager {
    private static final Logger logger = LoggerFactory.getLogger(ContextManager.class);
    
    private final DBConnection dbConnection;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;
    
    @Value("${agentpress.context.token-threshold:120000}")
    private int tokenThreshold;
    
    @Value("${agentpress.context.summary-target-tokens:10000}")
    private int summaryTargetTokens;
    
    @Value("${agentpress.context.reserve-tokens:5000}")
    private int reserveTokens;
    
    /**
     * Create a new context manager.
     * 
     * @param dbConnection The database connection
     * @param llmService The LLM service
     * @param objectMapper The object mapper
     */
    @Autowired
    public ContextManager(DBConnection dbConnection, LlmService llmService, ObjectMapper objectMapper) {
        this.dbConnection = dbConnection;
        this.llmService = llmService;
        this.objectMapper = objectMapper;
        logger.debug("Initialized ContextManager with token threshold: {}", tokenThreshold);
    }
    
    /**
     * Get the token threshold.
     * 
     * @return The token threshold
     */
    public int getTokenThreshold() {
        return tokenThreshold;
    }
    
    /**
     * Set the token threshold.
     * 
     * @param tokenThreshold The token threshold
     */
    public void setTokenThreshold(int tokenThreshold) {
        this.tokenThreshold = tokenThreshold;
    }
    
    /**
     * Get the current token count for a thread.
     * 
     * @param threadId The ID of the thread to analyze
     * @return A CompletableFuture that completes with the token count
     */
    public CompletableFuture<Integer> getThreadTokenCount(String threadId) {
        logger.debug("Getting token count for thread {}", threadId);
        
        return getMessagesForSummarization(threadId)
                .thenApply(messages -> {
                    if (messages.isEmpty()) {
                        logger.debug("No messages found for thread {}", threadId);
                        return 0;
                    }
                    
                    int tokenCount = estimateTokenCount(messages);
                    
                    logger.info("Thread {} has approximately {} tokens", threadId, tokenCount);
                    return tokenCount;
                });
    }
    
    /**
     * Estimate the token count for a list of messages.
     * This is a heuristic-based approximation, not a precise tokenization.
     * It aims to be more accurate than a simple word count by considering common tokenization patterns.
     * 
     * @param messages The messages to count tokens for
     * @return The estimated token count
     */
    private int estimateTokenCount(List<LlmMessage> messages) {
        int totalTokens = 0;
        
        for (LlmMessage message : messages) {
            int messageTokens = 0;
            
            // Add tokens for role (typically 1 token per role)
            if (message.getRole() != null) {
                messageTokens += 1; 
            }
            
            // Add tokens for name (if present, typically 1 token)
            if (message.getName() != null) {
                messageTokens += 1;
            }

            // Add tokens for tool_call_id (if present, typically 1 token)
            if (message.getToolCallId() != null) {
                messageTokens += 1;
            }
            
            // Add tokens for content
            if (message.getContent() != null) {
                if (message.getContent() instanceof String) {
                    messageTokens += countStringTokens((String) message.getContent());
                } else if (message.getContent() instanceof List) {
                    // Handle multi-modal content (list of parts)
                    for (Object part : (List<?>) message.getContent()) {
                        if (part instanceof Map) {
                            Map<String, Object> partMap = (Map<String, Object>) part;
                            String type = (String) partMap.get("type");
                            
                            if ("text".equals(type) && partMap.get("text") instanceof String) {
                                messageTokens += countStringTokens((String) partMap.get("text"));
                            } else if ("image_url".equals(type) || "image".equals(type)) {
                                // Images typically cost a fixed number of tokens or are based on detail level
                                // For approximation, assume a base cost for an image.
                                // A common estimate for a low-detail image is ~85 tokens, high-detail ~1100 tokens.
                                // Let's use a conservative average for estimation.
                                messageTokens += 500; // Placeholder: adjust based on actual LLM pricing/behavior
                            }
                            // Add tokens for other part types if necessary
                        }
                    }
                } else {
                    // Fallback for other unexpected content types
                    messageTokens += countStringTokens(message.getContent().toString());
                }
            }
            
            // Add tokens for tool calls (if present)
            if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
                for (ToolCall toolCall : message.getToolCalls()) {
                    // Function name and arguments contribute to token count
                    if (toolCall.getFunction() != null) {
                        messageTokens += countStringTokens(toolCall.getFunction().getName());
                        messageTokens += countStringTokens(toolCall.getFunction().getArguments());
                    }
                    // Add a small overhead for the tool_call structure itself
                    messageTokens += 4; 
                }
            }

            // Add a small overhead for the message structure itself (e.g., delimiters, newlines)
            messageTokens += 4; 
            
            totalTokens += messageTokens;
        }
        
        return totalTokens;
    }

    /**
     * Heuristic to estimate tokens for a given string.
     * This is a simplified model and does not replace a proper tokenization library.
     * 
     * @param text The text to estimate tokens for
     * @return The estimated token count
     */
    private int countStringTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        // A common heuristic is to divide character count by 4, or word count by 0.75
        // We'll use a combination: count words and add a small overhead for non-alphanumeric characters.
        
        // Split by whitespace to get words
        String[] words = text.trim().split("\\s+");
        int count = words.length; // Each word is at least one token
        
        // Add tokens for punctuation and special characters
        // This regex matches non-word characters (excluding whitespace)
        Pattern p = Pattern.compile("[^\\w\\s]");
        Matcher m = p.matcher(text);
        while (m.find()) {
            count++; // Each punctuation/special character often counts as a token
        }
        
        // Add a small buffer for sub-word tokenization and other complexities
        count += text.length() / 10; // 1 token per 10 characters as a general buffer
        
        return count;
    }
    
    /**
     * Get all LLM messages from the thread that need to be summarized.
     * 
     * @param threadId The ID of the thread to get messages from
     * @return A CompletableFuture that completes with the list of messages
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<List<LlmMessage>> getMessagesForSummarization(String threadId) {
        logger.debug("Getting messages for summarization for thread {}", threadId);
        
        return dbConnection.getMessages(threadId)
                .thenApply(messages -> {
                    try {
                        // Find the most recent summary message
                        Map<String, Object> lastSummary = messages.stream()
                                .filter(msg -> "summary".equals(msg.get("type")) && (Boolean) msg.get("is_llm_message"))
                                .max((m1, m2) -> ((String) m1.get("created_at")).compareTo((String) m2.get("created_at")))
                                .orElse(null);
                        
                        // Get messages after the most recent summary or all messages if no summary
                        List<Map<String, Object>> messagesToSummarize;
                        if (lastSummary != null) {
                            String lastSummaryTime = (String) lastSummary.get("created_at");
                            logger.debug("Found last summary at {}", lastSummaryTime);
                            
                            messagesToSummarize = messages.stream()
                                    .filter(msg -> ((String) msg.get("created_at")).compareTo(lastSummaryTime) > 0)
                                    .collect(Collectors.toList()); // Explicitly collect to List
                        } else {
                            logger.debug("No previous summary found, getting all messages");
                            messagesToSummarize = messages;
                        }
                        
                        // Convert to LlmMessage objects
                        return messagesToSummarize.stream()
                                .filter(msg -> (Boolean) msg.get("is_llm_message") && !"summary".equals(msg.get("type")))
                                .map(msg -> {
                                    try {
                                        Object content = msg.get("content");
                                        if (content instanceof String) {
                                            // If content is a JSON string, parse it
                                            content = objectMapper.readValue((String) content, Object.class);
                                        }
                                        
                                        String role = null;
                                        Object actualContent = null;
                                        List<ToolCall> toolCalls = null;

                                        if (content instanceof Map) {
                                            Map<String, Object> contentMap = (Map<String, Object>) content;
                                            role = (String) contentMap.get("role");
                                            actualContent = contentMap.get("content"); // This can be String or List
                                            
                                            // Parse tool_calls if present
                                            if (contentMap.containsKey("tool_calls") && contentMap.get("tool_calls") instanceof List) {
                                                toolCalls = ((List<Map<String, Object>>) contentMap.get("tool_calls")).stream()
                                                    .map(tcMap -> {
                                                        ToolCall tc = new ToolCall();
                                                        tc.setId((String) tcMap.get("id"));
                                                        tc.setType((String) tcMap.get("type"));
                                                        if (tcMap.containsKey("function") && tcMap.get("function") instanceof Map) {
                                                            Map<String, Object> funcMap = (Map<String, Object>) tcMap.get("function");
                                                            tc.setFunction(new ToolCall.FunctionCall((String) funcMap.get("name"), (String) funcMap.get("arguments")));
                                                        }
                                                        return tc;
                                                    })
                                                    .collect(Collectors.toList()); // Explicitly collect to List
                                            }
                                        } else if (content instanceof List) {
                                            // If the top-level content is a list, assume it's multi-modal
                                            role = (String) msg.get("role"); // Get role from original message
                                            actualContent = content;
                                        } else {
                                            // Fallback for simple string content
                                            role = (String) msg.get("role");
                                            actualContent = content;
                                        }
                                        
                                        LlmMessage llmMessage = new LlmMessage(role, actualContent);
                                        llmMessage.setToolCalls(toolCalls);
                                        llmMessage.setName((String) msg.get("name")); // Set name if available
                                        llmMessage.setToolCallId((String) msg.get("tool_call_id")); // Set tool_call_id if available
                                        return llmMessage;
                                    } catch (Exception e) {
                                        logger.error("Error parsing message content for summarization: {}", e.getMessage(), e);
                                        return null;
                                    }
                                })
                                .filter(llmMsg -> llmMsg != null)
                                .collect(Collectors.toList()); // Explicitly collect to List
                    } catch (Exception e) {
                        logger.error("Error getting messages for summarization: {}", e.getMessage(), e);
                        return List.of();
                    }
                });
    }
    
    /**
     * Generate a summary of conversation messages.
     * 
     * @param threadId The ID of the thread to summarize
     * @param messages The messages to summarize
     * @param model The LLM model to use for summarization
     * @param userId The ID of the user initiating the summarization
     * @param runId The ID of the agent run (if applicable)
     * @param startTime The start time of the LLM call for billing purposes
     * @return A CompletableFuture that completes with the summary message
     */
    public CompletableFuture<LlmMessage> createSummary(
            String threadId,
            List<LlmMessage> messages,
            String model,
            String userId,
            String runId,
            java.time.Instant startTime) {
        
        if (messages.isEmpty()) {
            logger.warn("No messages to summarize");
            return CompletableFuture.completedFuture(null);
        }
        
        logger.info("Creating summary for thread {} with {} messages", threadId, messages.size());
        
        // Create system message with summarization instructions
        LlmMessage systemMessage = new LlmMessage("system", 
                "You are a specialized summarization assistant. Your task is to create a concise but comprehensive " +
                "summary of the conversation history.\n\n" +
                "The summary should:\n" +
                "1. Preserve all key information including decisions, conclusions, and important context\n" +
                "2. Include any tools that were used and their results\n" +
                "3. Maintain chronological order of events\n" +
                "4. Be presented as a narrated list of key points with section headers\n" +
                "5. Include only factual information from the conversation (no new information)\n" +
                "6. Be concise but detailed enough that the conversation can continue with this summary as context\n\n" +
                "VERY IMPORTANT: This summary will replace older parts of the conversation in the LLM's context window, " +
                "so ensure it contains ALL key information and LATEST STATE OF THE CONVERSATION - " +
                "SO WE WILL KNOW HOW TO PICK UP WHERE WE LEFT OFF.\n\n\n" +
                "THE CONVERSATION HISTORY TO SUMMARIZE IS AS FOLLOWS:\n" +
                "===============================================================\n" +
                "==================== CONVERSATION HISTORY ====================\n" +
                formatMessagesForSummary(messages) + "\n" + // Format messages here
                "==================== END OF CONVERSATION HISTORY ====================\n" +
                "===============================================================\n");
        
        LlmMessage userMessage = new LlmMessage("user", "PLEASE PROVIDE THE SUMMARY NOW.");
        
        return llmService.makeLlmApiCall(
                List.of(systemMessage, userMessage),
                model != null ? model : "gpt-4o-mini",
                0.0f,
                summaryTargetTokens,
                null,
                null,
                false,
                false,
                null,
                userId, // Pass userId
                runId,  // Pass runId
                startTime // Pass startTime
            )
            .thenApply(response -> {
                try {
                    String summaryContent = response.getContent();
                    
                    // Format the summary message with clear beginning and end markers
                    String formattedSummary = "\n======== CONVERSATION HISTORY SUMMARY ========\n\n" +
                                             summaryContent + "\n\n" +
                                             "======== END OF SUMMARY ========\n\n" +
                                             "The above is a summary of the conversation history. " +
                                             "The conversation continues below.\n";
                    
                    return new LlmMessage("user", formattedSummary);
                } catch (Exception e) {
                    logger.error("Error creating summary: {}", e.getMessage(), e);
                    return null;
                }
            });
    }
    
    /**
     * Format a list of LlmMessage objects into a human-readable conversation string for summarization.
     * 
     * @param messages The list of LlmMessage objects
     * @return A formatted string representation of the conversation
     */
    private String formatMessagesForSummary(List<LlmMessage> messages) {
        StringBuilder formatted = new StringBuilder();
        for (LlmMessage message : messages) {
            // Add null checks to prevent NullPointerException
            String role = message.getRole();
            if (role == null) {
                role = "unknown"; // Use a default role if null
            }
            
            Object content = message.getContent();
            String contentStr = content != null ? content.toString() : ""; 
            
            formatted.append(role.toUpperCase()).append(": ").append(contentStr).append("\n\n");
        }
        return formatted.toString().trim(); // Trim trailing newlines
    }
    
    /**
     * Check if thread needs summarization and summarize if so.
     * 
     * @param threadId The ID of the thread to check
     * @param addMessageCallback Callback to add the summary message to the thread
     * @param model The LLM model to use for summarization
     * @param force Whether to force summarization regardless of token count
     * @param userId The ID of the user initiating the summarization
     * @param runId The ID of the agent run (if applicable)
     * @param startTime The start time of the LLM call for billing purposes
     * @return A CompletableFuture that completes with true if summarization was performed, false otherwise
     */
    public CompletableFuture<Boolean> checkAndSummarizeIfNeeded(
            String threadId,
            BiFunction<String, LlmMessage, CompletableFuture<Message>> addMessageCallback,
            String model,
            boolean force,
            String userId,
            String runId,
            java.time.Instant startTime) {
        
        return getThreadTokenCount(threadId)
                .thenCompose(tokenCount -> {
                    // If token count is below threshold and not forcing, no summarization needed
                    if (tokenCount < tokenThreshold && !force) {
                        logger.debug("Thread {} has {} tokens, below threshold {}", 
                                threadId, tokenCount, tokenThreshold);
                        return CompletableFuture.completedFuture(false);
                    }
                    
                    // Log reason for summarization
                    if (force) {
                        logger.info("Forced summarization of thread {} with {} tokens", threadId, tokenCount);
                    } else {
                        logger.info("Thread {} exceeds token threshold ({} >= {}), summarizing...",
                                threadId, tokenCount, tokenThreshold);
                    }
                    
                    // Get messages to summarize
                    return getMessagesForSummarization(threadId)
                            .thenCompose(messages -> {
                                // If there are too few messages, don't summarize
                                if (messages.size() < 3) {
                                    logger.info("Thread {} has too few messages ({}) to summarize",
                                            threadId, messages.size());
                                    return CompletableFuture.completedFuture(false);
                                }
                                
                                // Create summary
                                return createSummary(threadId, messages, model, userId, runId, startTime)
                                        .thenCompose(summary -> {
                                            if (summary != null) {
                                                // Add summary message to thread
                                                Map<String, Object> metadata = Map.of("token_count", tokenCount);
                                                
                                                return addMessageCallback.apply(threadId, summary)
                                                        .thenApply(savedMessage -> {
                                                            logger.info("Successfully added summary to thread {}", threadId);
                                                            return true;
                                                        });
                                            } else {
                                                logger.error("Failed to create summary for thread {}", threadId);
                                                return CompletableFuture.completedFuture(false);
                                            }
                                        });
                            });
                })
                .exceptionally(e -> {
                    logger.error("Error in check_and_summarize_if_needed: {}", e.getMessage(), e);
                    return false;
                });
    }
}
