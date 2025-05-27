package com.Nubian.ai.agentpress.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * Represents a message in a conversation thread.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
public class Message {
    private String messageId;
    private String threadId;
    private String type;
    private Object content;
    private boolean isLlmMessage;
    private Map<String, Object> metadata;
    private Instant createdAt;
    private Instant updatedAt;
    
    /**
     * Create a new message.
     */
    public Message() {
        this.messageId = UUID.randomUUID().toString();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
    
    /**
     * Create a new message with the specified parameters.
     * 
     * @param threadId The ID of the thread
     * @param type The type of the message
     * @param content The content of the message
     * @param isLlmMessage Whether the message is from the LLM
     * @param metadata Additional metadata
     */
    public Message(String threadId, String type, Object content, boolean isLlmMessage, Map<String, Object> metadata) {
        this();
        this.threadId = threadId;
        this.type = type;
        this.content = content;
        this.isLlmMessage = isLlmMessage;
        this.metadata = metadata;
    }
    
    /**
     * Get the message ID.
     * 
     * @return The message ID
     */
    public String getMessageId() {
        return messageId;
    }
    
    /**
     * Set the message ID.
     * 
     * @param messageId The message ID
     */
    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }
    
    /**
     * Get the thread ID.
     * 
     * @return The thread ID
     */
    public String getThreadId() {
        return threadId;
    }
    
    /**
     * Set the thread ID.
     * 
     * @param threadId The thread ID
     */
    public void setThreadId(String threadId) {
        this.threadId = threadId;
    }
    
    /**
     * Get the message type.
     * 
     * @return The message type
     */
    public String getType() {
        return type;
    }
    
    /**
     * Set the message type.
     * 
     * @param type The message type
     */
    public void setType(String type) {
        this.type = type;
    }
    
    /**
     * Get the message content.
     * 
     * @return The message content
     */
    public Object getContent() {
        return content;
    }
    
    /**
     * Set the message content.
     * 
     * @param content The message content
     */
    public void setContent(Object content) {
        this.content = content;
    }
    
    /**
     * Check if the message is from the LLM.
     * 
     * @return true if from the LLM, false otherwise
     */
    public boolean isLlmMessage() {
        return isLlmMessage;
    }
    
    /**
     * Set whether the message is from the LLM.
     * 
     * @param isLlmMessage Whether the message is from the LLM
     */
    public void setLlmMessage(boolean isLlmMessage) {
        this.isLlmMessage = isLlmMessage;
    }
    
    /**
     * Get the message metadata.
     * 
     * @return The message metadata
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    /**
     * Set the message metadata.
     * 
     * @param metadata The message metadata
     */
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
    
    /**
     * Get the creation time.
     * 
     * @return The creation time
     */
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    /**
     * Set the creation time.
     * 
     * @param createdAt The creation time
     */
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
    
    /**
     * Get the update time.
     * 
     * @return The update time
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }
    
    /**
     * Set the update time.
     * 
     * @param updatedAt The update time
     */
    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
