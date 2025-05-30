package com.nubian.ai.agentpress.mock;

import java.util.List;

/**
 * Mock implementation of the Tool class.
 */
public class Tool {
    private List<FunctionDeclaration> functionDeclarations;
    private ToolConfig toolConfig;
    
    private Tool(Builder builder) {
        this.functionDeclarations = builder.functionDeclarations;
        this.toolConfig = builder.toolConfig;
    }
    
    public List<FunctionDeclaration> getFunctionDeclarations() {
        return functionDeclarations;
    }
    
    public ToolConfig getToolConfig() {
        return toolConfig;
    }
    
    /**
     * Builder for Tool.
     */
    public static class Builder {
        private List<FunctionDeclaration> functionDeclarations;
        private ToolConfig toolConfig;
        
        public Builder setFunctionDeclarations(List<FunctionDeclaration> functionDeclarations) {
            this.functionDeclarations = functionDeclarations;
            return this;
        }
        
        public Builder setToolConfig(ToolConfig toolConfig) {
            this.toolConfig = toolConfig;
            return this;
        }
        
        public Tool build() {
            return new Tool(this);
        }
    }
    
    /**
     * Create a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Configuration for tools.
     */
    public static class ToolConfig {
        private boolean requireTool;
        
        private ToolConfig(Builder builder) {
            this.requireTool = builder.requireTool;
        }
        
        public boolean isRequireTool() {
            return requireTool;
        }
        
        /**
         * Builder for ToolConfig.
         */
        public static class Builder {
            private boolean requireTool;
            
            public Builder setRequireTool(boolean requireTool) {
                this.requireTool = requireTool;
                return this;
            }
            
            public ToolConfig build() {
                return new ToolConfig(this);
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
