package com.nubian.ai.agentpress.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * Represents a tool call in an LLM message.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
public class ToolCall {
    private String id;
    private String type;
    private FunctionCall function;
    
    /**
     * Create a new tool call.
     */
    public ToolCall() {
    }
    
    /**
     * Create a new tool call with the specified ID, type, and function.
     * 
     * @param id The ID of the tool call
     * @param type The type of the tool call
     * @param function The function call
     */
    public ToolCall(String id, String type, FunctionCall function) {
        this.id = id;
        this.type = type;
        this.function = function;
    }
    
    /**
     * Get the ID of the tool call.
     * 
     * @return The ID
     */
    public String getId() {
        return id;
    }
    
    /**
     * Set the ID of the tool call.
     * 
     * @param id The ID
     */
    public void setId(String id) {
        this.id = id;
    }
    
    /**
     * Get the type of the tool call.
     * 
     * @return The type
     */
    public String getType() {
        return type;
    }
    
    /**
     * Set the type of the tool call.
     * 
     * @param type The type
     */
    public void setType(String type) {
        this.type = type;
    }
    
    /**
     * Get the function call.
     * 
     * @return The function call
     */
    public FunctionCall getFunction() {
        return function;
    }
    
    /**
     * Set the function call.
     * 
     * @param function The function call
     */
    public void setFunction(FunctionCall function) {
        this.function = function;
    }
    
    /**
     * Represents a function call in a tool call.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(Include.NON_NULL)
    public static class FunctionCall {
        private String name;
        private String arguments;
        
        /**
         * Create a new function call.
         */
        public FunctionCall() {
        }
        
        /**
         * Create a new function call with the specified name and arguments.
         * 
         * @param name The name of the function
         * @param arguments The arguments to the function
         */
        public FunctionCall(String name, String arguments) {
            this.name = name;
            this.arguments = arguments;
        }
        
        /**
         * Get the name of the function.
         * 
         * @return The name
         */
        public String getName() {
            return name;
        }
        
        /**
         * Set the name of the function.
         * 
         * @param name The name
         */
        public void setName(String name) {
            this.name = name;
        }
        
        /**
         * Get the arguments to the function.
         * 
         * @return The arguments
         */
        public String getArguments() {
            return arguments;
        }
        
        /**
         * Set the arguments to the function.
         * 
         * @param arguments The arguments
         */
        public void setArguments(String arguments) {
            this.arguments = arguments;
        }
    }
}
