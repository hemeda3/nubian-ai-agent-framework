package com.nubian.ai.agentpress.mock;

/**
 * Mock implementation of the FunctionDeclaration class.
 */
public class FunctionDeclaration {
    private String name;
    private String description;
    private Schema parameters;
    
    private FunctionDeclaration(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.parameters = builder.parameters;
    }
    
    public String getName() {
        return name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public Schema getParameters() {
        return parameters;
    }
    
    /**
     * Builder for FunctionDeclaration.
     */
    public static class Builder {
        private String name;
        private String description;
        private Schema parameters;
        
        public Builder setName(String name) {
            this.name = name;
            return this;
        }
        
        public Builder setDescription(String description) {
            this.description = description;
            return this;
        }
        
        public Builder setParameters(Schema parameters) {
            this.parameters = parameters;
            return this;
        }
        
        public FunctionDeclaration build() {
            return new FunctionDeclaration(this);
        }
    }
    
    /**
     * Create a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }
}
