package com.Nubian.ai.agentpress.model;

/**
 * Enumeration of supported schema types for tool definitions.
 */
public enum SchemaType {
    /**
     * OpenAPI schema for function calling.
     */
    OPENAPI("openapi"),
    
    /**
     * XML schema for XML-based tool calling.
     */
    XML("xml"),
    
    /**
     * Custom schema for specialized tool definitions.
     */
    CUSTOM("custom");
    
    private final String value;
    
    SchemaType(String value) {
        this.value = value;
    }
    
    /**
     * Get the string value of the schema type.
     * 
     * @return The string value
     */
    public String getValue() {
        return value;
    }
    
    @Override
    public String toString() {
        return value;
    }
}
