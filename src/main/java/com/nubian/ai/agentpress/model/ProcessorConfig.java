package com.nubian.ai.agentpress.model;

/**
 * Configuration for response processing and tool execution.
 * 
 * This class controls how the LLM's responses are processed, including how tool calls
 * are detected, executed, and their results handled.
 */
public class ProcessorConfig {
    /**
     * Enable XML-based tool call detection (<tool>...</tool>).
     */
    private boolean xmlToolCalling = true;
    
    /**
     * Enable OpenAI-style function calling format.
     */
    private boolean nativeToolCalling = false;
    
    /**
     * Whether to automatically execute detected tool calls.
     */
    private boolean executeTools = true;
    
    /**
     * For streaming, execute tools as they appear vs. at the end.
     */
    private boolean executeOnStream = false;
    
    /**
     * How to execute multiple tools ("sequential" or "parallel").
     */
    private String toolExecutionStrategy = "sequential";
    
    /**
     * How to add XML tool results to the conversation.
     */
    private String xmlAddingStrategy = "assistant_message";
    
    /**
     * Maximum number of XML tool calls to process (0 = no limit).
     */
    private int maxXmlToolCalls = 0;
    
    /**
     * Create a new processor configuration with default values.
     */
    public ProcessorConfig() {
    }
    
    /**
     * Validate configuration.
     * 
     * @throws IllegalArgumentException if the configuration is invalid
     */
    public void validate() {
        if (!xmlToolCalling && !nativeToolCalling && executeTools) {
            throw new IllegalArgumentException("At least one tool calling format (XML or native) must be enabled if execute_tools is true");
        }
        
        if (!("user_message".equals(xmlAddingStrategy) || 
              "assistant_message".equals(xmlAddingStrategy) || 
              "inline_edit".equals(xmlAddingStrategy))) {
            throw new IllegalArgumentException("xml_adding_strategy must be 'user_message', 'assistant_message', or 'inline_edit'");
        }
        
        if (maxXmlToolCalls < 0) {
            throw new IllegalArgumentException("max_xml_tool_calls must be a non-negative integer (0 = no limit)");
        }
    }
    
    /**
     * Check if XML tool calling is enabled.
     * 
     * @return true if enabled, false otherwise
     */
    public boolean isXmlToolCalling() {
        return xmlToolCalling;
    }
    
    /**
     * Set whether XML tool calling is enabled.
     * 
     * @param xmlToolCalling Whether XML tool calling is enabled
     * @return this configuration for chaining
     */
    public ProcessorConfig setXmlToolCalling(boolean xmlToolCalling) {
        this.xmlToolCalling = xmlToolCalling;
        return this;
    }
    
    /**
     * Check if native tool calling is enabled.
     * 
     * @return true if enabled, false otherwise
     */
    public boolean isNativeToolCalling() {
        return nativeToolCalling;
    }
    
    /**
     * Set whether native tool calling is enabled.
     * 
     * @param nativeToolCalling Whether native tool calling is enabled
     * @return this configuration for chaining
     */
    public ProcessorConfig setNativeToolCalling(boolean nativeToolCalling) {
        this.nativeToolCalling = nativeToolCalling;
        return this;
    }
    
    /**
     * Check if tool execution is enabled.
     * 
     * @return true if enabled, false otherwise
     */
    public boolean isExecuteTools() {
        return executeTools;
    }
    
    /**
     * Set whether tool execution is enabled.
     * 
     * @param executeTools Whether tool execution is enabled
     * @return this configuration for chaining
     */
    public ProcessorConfig setExecuteTools(boolean executeTools) {
        this.executeTools = executeTools;
        return this;
    }
    
    /**
     * Check if tool execution on stream is enabled.
     * 
     * @return true if enabled, false otherwise
     */
    public boolean isExecuteOnStream() {
        return executeOnStream;
    }
    
    /**
     * Set whether tool execution on stream is enabled.
     * 
     * @param executeOnStream Whether tool execution on stream is enabled
     * @return this configuration for chaining
     */
    public ProcessorConfig setExecuteOnStream(boolean executeOnStream) {
        this.executeOnStream = executeOnStream;
        return this;
    }
    
    /**
     * Get the tool execution strategy.
     * 
     * @return The tool execution strategy
     */
    public String getToolExecutionStrategy() {
        return toolExecutionStrategy;
    }
    
    /**
     * Set the tool execution strategy.
     * 
     * @param toolExecutionStrategy The tool execution strategy
     * @return this configuration for chaining
     */
    public ProcessorConfig setToolExecutionStrategy(String toolExecutionStrategy) {
        this.toolExecutionStrategy = toolExecutionStrategy;
        return this;
    }
    
    /**
     * Get the XML adding strategy.
     * 
     * @return The XML adding strategy
     */
    public String getXmlAddingStrategy() {
        return xmlAddingStrategy;
    }
    
    /**
     * Set the XML adding strategy.
     * 
     * @param xmlAddingStrategy The XML adding strategy
     * @return this configuration for chaining
     */
    public ProcessorConfig setXmlAddingStrategy(String xmlAddingStrategy) {
        this.xmlAddingStrategy = xmlAddingStrategy;
        return this;
    }
    
    /**
     * Get the maximum number of XML tool calls.
     * 
     * @return The maximum number of XML tool calls
     */
    public int getMaxXmlToolCalls() {
        return maxXmlToolCalls;
    }
    
    /**
     * Set the maximum number of XML tool calls.
     * 
     * @param maxXmlToolCalls The maximum number of XML tool calls
     * @return this configuration for chaining
     */
    public ProcessorConfig setMaxXmlToolCalls(int maxXmlToolCalls) {
        this.maxXmlToolCalls = maxXmlToolCalls;
        return this;
    }
}
