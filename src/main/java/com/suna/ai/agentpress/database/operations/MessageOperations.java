package com.Nubian.ai.agentpress.database.operations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.Nubian.ai.agentpress.database.ResilientSupabaseClient;
import com.Nubian.ai.agentpress.model.Message;

/**
 * Handles message-specific database operations.
 */
@Component
public class MessageOperations {

    private static final Logger logger = LoggerFactory.getLogger(MessageOperations.class);

    private final ResilientSupabaseClient supabaseClient;
    private final ObjectMapper objectMapper;

    public MessageOperations(ResilientSupabaseClient supabaseClient, ObjectMapper objectMapper) {
        this.supabaseClient = supabaseClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Insert a message into the database.
     * 
     * @param message The message to insert
     * @return A CompletableFuture that completes with the inserted message
     */
    public CompletableFuture<Message> insertMessage(Message message) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("Preparing to upsert message ID '{}' into thread '{}'", 
                        message.getMessageId(), message.getThreadId());
                
                // Serialize message content
                String contentJson;
                if (message.getContent() instanceof String) {
                    contentJson = (String) message.getContent();
                } else {
                    contentJson = objectMapper.writeValueAsString(message.getContent());
                }
                
                String metadataJson = "{}";
                if (message.getMetadata() != null) {
                    metadataJson = objectMapper.writeValueAsString(message.getMetadata());
                }
                
                Map<String, Object> data = new HashMap<>();
                data.put("message_id", message.getMessageId());
                data.put("thread_id", message.getThreadId());
                data.put("type", message.getType());
                data.put("content", contentJson);
                data.put("is_llm_message", message.isLlmMessage());
                data.put("metadata", metadataJson);
                
                // Use upsert to handle duplicate message IDs gracefully
                try {
                    Map<String, Object> result = supabaseClient.insert("messages", data, true).join();
                    logger.info("Successfully upserted message to thread '{}'", message.getThreadId());
                    return message;
                } catch (Exception e) {
                    logger.error("Failed to upsert message to thread '{}': {}", 
                            message.getThreadId(), e.getMessage(), e);
                    throw new RuntimeException("Failed to upsert message to thread", e);
                }
            } catch (JsonProcessingException e) {
                logger.error("Failed to serialize message content or metadata: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to serialize message content or metadata", e);
            } catch (Exception e) {
                logger.error("Failed to add message to thread {}: {}", 
                        message.getThreadId(), e.getMessage(), e);
                throw new RuntimeException("Failed to add message to thread", e);
            }
        });
    }
    
    /**
     * Get all messages for a thread.
     * 
     * @param threadId The ID of the thread
     * @return A CompletableFuture that completes with the list of messages
     */
    public CompletableFuture<List<Map<String, Object>>> getMessages(String threadId) {
        Map<String, Object> conditions = new HashMap<>();
        conditions.put("thread_id", threadId);
        
        return supabaseClient.queryForList("messages", conditions)
            .thenApply(messages -> {
                logger.debug("Retrieved {} messages for thread {}", messages.size(), threadId);
                return messages;
            })
            .exceptionally(e -> {
                logger.error("Failed to get messages for thread {}: {}", threadId, e.getMessage(), e);
                throw new RuntimeException("Failed to get messages for thread", e);
            });
    }
    
    /**
     * Get LLM-formatted messages for a thread.
     * 
     * @param threadId The ID of the thread
     * @return A CompletableFuture that completes with the list of LLM-formatted messages
     */
    public CompletableFuture<List<Map<String, Object>>> getLlmFormattedMessages(String threadId) {
        logger.debug("Getting LLM-formatted messages for thread {}", threadId);
        
        // Direct query to messages table instead of using the get_llm_formatted_messages function
        Map<String, Object> conditions = new HashMap<>();
        conditions.put("thread_id", threadId);
        conditions.put("is_llm_message", true);
        
        return supabaseClient.queryForList("messages", conditions)
            .thenApply(messages -> {
                logger.debug("Retrieved {} LLM-formatted messages for thread {}", messages.size(), threadId);
                
                // Transform message contents if stored as strings
                for (Map<String, Object> message : messages) {
                    if (message.containsKey("content") && message.get("content") instanceof String) {
                        try {
                            String contentStr = (String) message.get("content");
                            // Try to parse JSON string to object if it's stored as a string
                            @SuppressWarnings("unchecked")
                            Map<String, Object> contentMap = objectMapper.readValue(contentStr, Map.class);
                            message.put("content", contentMap);
                        } catch (Exception e) {
                            // If it fails to parse, keep it as a string
                            logger.warn("Could not parse message content as JSON: {}", e.getMessage());
                        }
                    }
                }
                
                return messages;
            })
            .exceptionally(e -> {
                logger.error("Failed to get LLM-formatted messages for thread {}: {}", 
                        threadId, e.getMessage(), e);
                throw new RuntimeException("Failed to get LLM-formatted messages for thread", e);
            });
    }

    /**
     * Delete messages of a specific type for a given thread.
     *
     * @param threadId The ID of the thread
     * @param messageType The type of messages to delete
     * @return A CompletableFuture that completes with the number of deleted messages
     */
    public CompletableFuture<Integer> deleteMessagesByType(String threadId, String messageType) {
        Map<String, Object> conditions = new HashMap<>();
        conditions.put("thread_id", threadId);
        conditions.put("type", messageType);
        
        return supabaseClient.delete("messages", conditions)
            .thenApply(rowsAffected -> {
                logger.info("Deleted {} messages of type '{}' for thread {}", rowsAffected, messageType, threadId);
                return rowsAffected;
            })
            .exceptionally(e -> {
                logger.error("Failed to delete messages of type '{}' for thread {}: {}",
                        messageType, threadId, e.getMessage(), e);
                throw new RuntimeException("Failed to delete messages by type", e);
            });
    }
}
