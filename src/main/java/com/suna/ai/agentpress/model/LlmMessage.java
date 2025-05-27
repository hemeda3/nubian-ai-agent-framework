package com.Nubian.ai.agentpress.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a message in the LLM conversation format.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
public class LlmMessage {
    private String role;
    private Object content; // Change type to Object for multi-modal support
    private List<ToolCall> toolCalls;
    private String name;
    
    @JsonProperty("tool_call_id")
    private String toolCallId;
    
    /**
     * Create a new LLM message.
     */
    public LlmMessage() {
    }
    
    /**
     * Create a new LLM message with the specified role and content.
     * 
     * @param role The role of the message sender
     * @param content The content of the message (can be String or List<Object>)
     */
    public LlmMessage(String role, Object content) {
        this.role = role;
        this.content = content;
    }
    
    /**
     * Create a new LLM message with the specified role, content, and tool calls.
     * 
     * @param role The role of the message sender
     * @param content The content of the message (can be String or List<Object>)
     * @param toolCalls The tool calls in the message
     */
    public LlmMessage(String role, Object content, List<ToolCall> toolCalls) {
        this.role = role;
        this.content = content;
        this.toolCalls = toolCalls;
    }
    
    /**
     * Create a new tool result message.
     * 
     * @param toolCallId The ID of the tool call
     * @param name The name of the tool
     * @param content The content of the message (can be String or List<Object>)
     */
    public LlmMessage(String toolCallId, String name, Object content) {
        this.role = "tool";
        this.toolCallId = toolCallId;
        this.name = name;
        this.content = content;
    }
    
    /**
     * Get the role of the message sender.
     * 
     * @return The role
     */
    public String getRole() {
        return role;
    }
    
    /**
     * Set the role of the message sender.
     * 
     * @param role The role
     */
    public void setRole(String role) {
        this.role = role;
    }
    
    /**
     * Get the content of the message.
     * 
     * @return The content
     */
    public Object getContent() {
        return content;
    }
    
    /**
     * Set the content of the message.
     * 
     * @param content The content (can be String or List<Object>)
     */
    public void setContent(Object content) {
        this.content = content;
    }
    
    /**
     * Get the tool calls in the message.
     * 
     * @return The tool calls
     */
    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }
    
    /**
     * Set the tool calls in the message.
     * 
     * @param toolCalls The tool calls
     */
    public void setToolCalls(List<ToolCall> toolCalls) {
        this.toolCalls = toolCalls;
    }
    
    /**
     * Get the name of the tool.
     * 
     * @return The name
     */
    public String getName() {
        return name;
    }
    
    /**
     * Set the name of the tool.
     * 
     * @param name The name
     */
    public void setName(String name) {
        this.name = name;
    }
    
    /**
     * Get the ID of the tool call.
     * 
     * @return The tool call ID
     */
    public String getToolCallId() {
        return toolCallId;
    }
    
    /**
     * Set the ID of the tool call.
     * 
     * @param toolCallId The tool call ID
     */
    public void setToolCallId(String toolCallId) {
        this.toolCallId = toolCallId;
    }
}
