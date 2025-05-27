package com.Nubian.ai.agentpress.model;

import lombok.Builder; // Import Lombok Builder

/**
 * Container for tool execution results.
 */
@Builder // Add Builder annotation
public class ToolResult {
    private final boolean success;
    private final Object output;
    
    /**
     * Create a new tool result.
     * 
     * @param success Whether the tool execution succeeded
     * @param output Output message, data, or error description
     */
    public ToolResult(boolean success, Object output) {
        this.success = success;
        this.output = output;
    }
    
    /**
     * Check if the tool execution was successful.
     * 
     * @return true if successful, false otherwise
     */
    public boolean isSuccess() {
        return success;
    }
    
    /**
     * Get the output of the tool execution.
     * 
     * @return The output object
     */
    public Object getOutput() {
        return output;
    }
    
    @Override
    public String toString() {
        return "ToolResult{success=" + success + ", output='" + output + "'}";
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        ToolResult that = (ToolResult) o;
        
        if (success != that.success) return false;
        return output != null ? output.equals(that.output) : that.output == null;
    }
    
    @Override
    public int hashCode() {
        int result = (success ? 1 : 0);
        result = 31 * result + (output != null ? output.hashCode() : 0);
        return result;
    }
}
