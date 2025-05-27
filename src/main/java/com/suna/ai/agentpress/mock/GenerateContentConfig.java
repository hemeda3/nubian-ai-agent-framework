package com.Nubian.ai.agentpress.mock;

import java.util.List;

/**
 * Mock implementation of the GenerateContentConfig class.
 */
public class GenerateContentConfig {
    private float temperature;
    private Integer maxOutputTokens;
    private List<Tool> tools;
    private Tool.ToolConfig toolConfig;
    private ReasoningConfig reasoningConfig;
    
    private GenerateContentConfig(Builder builder) {
        this.temperature = builder.temperature;
        this.maxOutputTokens = builder.maxOutputTokens;
        this.tools = builder.tools;
        this.toolConfig = builder.toolConfig;
        this.reasoningConfig = builder.reasoningConfig;
    }
    
    public float getTemperature() {
        return temperature;
    }
    
    public Integer getMaxOutputTokens() {
        return maxOutputTokens;
    }
    
    public List<Tool> getTools() {
        return tools;
    }
    
    public Tool.ToolConfig getToolConfig() {
        return toolConfig;
    }
    
    public ReasoningConfig getReasoningConfig() {
        return reasoningConfig;
    }
    
    /**
     * Builder for GenerateContentConfig.
     */
    public static class Builder {
        private float temperature = 0.7f;
        private Integer maxOutputTokens;
        private List<Tool> tools;
        private Tool.ToolConfig toolConfig;
        private ReasoningConfig reasoningConfig;
        
        public Builder setTemperature(float temperature) {
            this.temperature = temperature;
            return this;
        }
        
        public Builder setMaxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
            return this;
        }
        
        public Builder setTools(List<Tool> tools) {
            this.tools = tools;
            return this;
        }
        
        public Builder setToolConfig(Tool.ToolConfig toolConfig) {
            this.toolConfig = toolConfig;
            return this;
        }
        
        public Builder setReasoningConfig(ReasoningConfig reasoningConfig) {
            this.reasoningConfig = reasoningConfig;
            return this;
        }
        
        public GenerateContentConfig build() {
            return new GenerateContentConfig(this);
        }
    }
    
    /**
     * Create a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Configuration for reasoning.
     */
    public static class ReasoningConfig {
        private boolean enableReasoning;
        private String reasoningEffort;
        
        private ReasoningConfig(Builder builder) {
            this.enableReasoning = builder.enableReasoning;
            this.reasoningEffort = builder.reasoningEffort;
        }
        
        public boolean isEnableReasoning() {
            return enableReasoning;
        }
        
        public String getReasoningEffort() {
            return reasoningEffort;
        }
        
        /**
         * Builder for ReasoningConfig.
         */
        public static class Builder {
            private boolean enableReasoning;
            private String reasoningEffort = "low";
            
            public Builder setEnableReasoning(boolean enableReasoning) {
                this.enableReasoning = enableReasoning;
                return this;
            }
            
            public Builder setReasoningEffort(String reasoningEffort) {
                this.reasoningEffort = reasoningEffort;
                return this;
            }
            
            public ReasoningConfig build() {
                return new ReasoningConfig(this);
            }
        }
        
        /**
         * Create a new builder.
         */
        public static Builder builder() {
            return new Builder();
        }
    }
}
