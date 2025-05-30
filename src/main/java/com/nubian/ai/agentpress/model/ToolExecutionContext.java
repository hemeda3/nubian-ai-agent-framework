package com.nubian.ai.agentpress.model;

import java.util.Map;

/**
 * Context for a tool execution including call details, result, and display info.
 */
public class ToolExecutionContext {
    private final Map<String, Object> toolCall;
    private final int toolIndex;
    private ToolResult result;
    private String functionName;
    private String xmlTagName;
    private Exception error;
    private String assistantMessageId;
    private Map<String, Object> parsingDetails;
    
    /**
     * Create a new tool execution context.
     * 
     * @param toolCall The tool call
     * @param toolIndex The index of the tool in the sequence
     */
    public ToolExecutionContext(Map<String, Object> toolCall, int toolIndex) {
        this.toolCall = toolCall;
        this.toolIndex = toolIndex;
        
        // Extract function name and XML tag name from tool call
        if (toolCall != null) {
            if (toolCall.containsKey("function_name")) {
                this.functionName = (String) toolCall.get("function_name");
            } else if (toolCall.containsKey("function")) {
                Map<String, Object> functionInfo = (Map<String, Object>) toolCall.get("function");
                if (functionInfo != null && functionInfo.containsKey("name")) {
                    this.functionName = (String) functionInfo.get("name");
                }
            }
            
            this.xmlTagName = (String) toolCall.get("xml_tag_name");
        }
    }
    
    /**
     * Get the tool call.
     * 
     * @return The tool call
     */
    public Map<String, Object> getToolCall() {
        return toolCall;
    }
    
    /**
     * Get the tool index.
     * 
     * @return The tool index
     */
    public int getToolIndex() {
        return toolIndex;
    }
    
    /**
     * Get the result.
     * 
     * @return The result
     */
    public ToolResult getResult() {
        return result;
    }
    
    /**
     * Set the result.
     * 
     * @param result The result
     */
    public void setResult(ToolResult result) {
        this.result = result;
    }
    
    /**
     * Get the function name.
     * 
     * @return The function name
     */
    public String getFunctionName() {
        return functionName;
    }
    
    /**
     * Set the function name.
     * 
     * @param functionName The function name
     */
    public void setFunctionName(String functionName) {
        this.functionName = functionName;
    }
    
    /**
     * Get the XML tag name.
     * 
     * @return The XML tag name
     */
    public String getXmlTagName() {
        return xmlTagName;
    }
    
    /**
     * Set the XML tag name.
     * 
     * @param xmlTagName The XML tag name
     */
    public void setXmlTagName(String xmlTagName) {
        this.xmlTagName = xmlTagName;
    }
    
    /**
     * Get the error.
     * 
     * @return The error
     */
    public Exception getError() {
        return error;
    }
    
    /**
     * Set the error.
     * 
     * @param error The error
     */
    public void setError(Exception error) {
        this.error = error;
    }
    
    /**
     * Get the assistant message ID.
     * 
     * @return The assistant message ID
     */
    public String getAssistantMessageId() {
        return assistantMessageId;
    }
    
    /**
     * Set the assistant message ID.
     * 
     * @param assistantMessageId The assistant message ID
     */
    public void setAssistantMessageId(String assistantMessageId) {
        this.assistantMessageId = assistantMessageId;
    }
    
    /**
     * Get the parsing details.
     * 
     * @return The parsing details
     */
    public Map<String, Object> getParsingDetails() {
        return parsingDetails;
    }
    
    /**
     * Set the parsing details.
     * 
     * @param parsingDetails The parsing details
     */
    public void setParsingDetails(Map<String, Object> parsingDetails) {
        this.parsingDetails = parsingDetails;
    }
}
