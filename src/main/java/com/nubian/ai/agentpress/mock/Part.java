package com.nubian.ai.agentpress.mock;

/**
 * Mock implementation of the Part class.
 */
public class Part {
    private String text;
    
    private Part(Builder builder) {
        this.text = builder.text;
    }
    
    public String getText() {
        return text;
    }
    
    /**
     * Builder for Part.
     */
    public static class Builder {
        private String text;
        
        public Builder setText(String text) {
            this.text = text;
            return this;
        }
        
        public Part build() {
            return new Part(this);
        }
    }
    
    /**
     * Create a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }
}
