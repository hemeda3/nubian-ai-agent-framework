package com.Nubian.ai.agentpress.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a response from an LLM API call.
 * This class is designed to be a unified response format for all LLM providers,
 * eliminating the need for mock Google GenAI classes.
 */
public class LlmResponse {
    private String id;
    private String model;
    private String content;
    private List<ToolCall> toolCalls;
    private int promptTokens;
    private int completionTokens;
    private int totalTokens;
    private String finishReason;
    private List<Candidate> candidates;
    
    /**
     * Candidate class representing a generated response.
     */
    public static class Candidate {
        private Content content;
        private String finishReason;
        
        public Content getContent() {
            return content;
        }
        
        public void setContent(Content content) {
            this.content = content;
        }
        
        public String getFinishReason() {
            return finishReason;
        }
        
        public void setFinishReason(String finishReason) {
            this.finishReason = finishReason;
        }
    }
    
    /**
     * Content class representing the content of a response.
     */
    public static class Content {
        private String role;
        private List<Part> parts;
        
        public String getRole() {
            return role;
        }
        
        public void setRole(String role) {
            this.role = role;
        }
        
        public List<Part> getParts() {
            return parts;
        }
        
        public void setParts(List<Part> parts) {
            this.parts = parts;
        }
        
        /**
         * Builder for Content.
         */
        public static class Builder {
            private String role;
            private List<Part> parts = new ArrayList<>();
            
            public Builder setRole(String role) {
                this.role = role;
                return this;
            }
            
            public Builder addPart(Part part) {
                this.parts.add(part);
                return this;
            }
            
            public Content build() {
                Content content = new Content();
                content.role = this.role;
                content.parts = this.parts;
                return content;
            }
        }
        
        /**
         * Create a new builder.
         */
        public static Builder builder() {
            return new Builder();
        }
    }
    
    /**
     * Part class representing a part of content.
     */
    public static class Part {
        private String text;
        
        public String getText() {
            return text;
        }
        
        public void setText(String text) {
            this.text = text;
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
                Part part = new Part();
                part.text = this.text;
                return part;
            }
        }
        
        /**
         * Create a new builder.
         */
        public static Builder builder() {
            return new Builder();
        }
    }
    
    /**
     * Create a new LlmResponse.
     */
    public LlmResponse() {
        this.candidates = new ArrayList<>();
    }
    
    /**
     * Create a new LlmResponse with the specified parameters.
     * 
     * @param id The ID of the response
     * @param model The model used to generate the response
     * @param content The content of the response
     * @param toolCalls The tool calls in the response
     * @param promptTokens The number of tokens in the prompt
     * @param completionTokens The number of tokens in the completion
     * @param totalTokens The total number of tokens
     * @param finishReason The reason the response was finished
     */
    public LlmResponse(
            String id,
            String model,
            String content,
            List<ToolCall> toolCalls,
            int promptTokens,
            int completionTokens,
            int totalTokens,
            String finishReason) {
        this.id = id;
        this.model = model;
        this.content = content;
        this.toolCalls = toolCalls;
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.finishReason = finishReason;
        this.candidates = new ArrayList<>();
        
        // Create a default candidate with the content
        if (content != null) {
            Candidate candidate = new Candidate();
            Content contentObj = new Content();
            contentObj.setRole("assistant");
            Part part = new Part();
            part.setText(content);
            List<Part> parts = new ArrayList<>();
            parts.add(part);
            contentObj.setParts(parts);
            candidate.setContent(contentObj);
            candidate.setFinishReason(finishReason);
            this.candidates.add(candidate);
        }
    }
    
    /**
     * Get the ID of the response.
     * 
     * @return The ID
     */
    public String getId() {
        return id;
    }
    
    /**
     * Set the ID of the response.
     * 
     * @param id The ID
     */
    public void setId(String id) {
        this.id = id;
    }
    
    /**
     * Get the model used to generate the response.
     * 
     * @return The model
     */
    public String getModel() {
        return model;
    }
    
    /**
     * Set the model used to generate the response.
     * 
     * @param model The model
     */
    public void setModel(String model) {
        this.model = model;
    }
    
    /**
     * Get the content of the response.
     * 
     * @return The content
     */
    public String getContent() {
        return content;
    }
    
    /**
     * Set the content of the response.
     * 
     * @param content The content
     */
    public void setContent(String content) {
        this.content = content;
        
        // Update the first candidate's content if it exists
        if (this.candidates != null && !this.candidates.isEmpty() && content != null) {
            Candidate candidate = this.candidates.get(0);
            if (candidate.getContent() == null) {
                Content contentObj = new Content();
                contentObj.setRole("assistant");
                List<Part> parts = new ArrayList<>();
                Part part = new Part();
                part.setText(content);
                parts.add(part);
                contentObj.setParts(parts);
                candidate.setContent(contentObj);
            } else {
                if (candidate.getContent().getParts() != null && !candidate.getContent().getParts().isEmpty()) {
                    candidate.getContent().getParts().get(0).setText(content);
                }
            }
        }
    }
    
    /**
     * Get the tool calls in the response.
     * 
     * @return The tool calls
     */
    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }
    
    /**
     * Set the tool calls in the response.
     * 
     * @param toolCalls The tool calls
     */
    public void setToolCalls(List<ToolCall> toolCalls) {
        this.toolCalls = toolCalls;
    }
    
    /**
     * Get the number of tokens in the prompt.
     * 
     * @return The number of tokens
     */
    public int getPromptTokens() {
        return promptTokens;
    }
    
    /**
     * Set the number of tokens in the prompt.
     * 
     * @param promptTokens The number of tokens
     */
    public void setPromptTokens(int promptTokens) {
        this.promptTokens = promptTokens;
    }
    
    /**
     * Get the number of tokens in the completion.
     * 
     * @return The number of tokens
     */
    public int getCompletionTokens() {
        return completionTokens;
    }
    
    /**
     * Set the number of tokens in the completion.
     * 
     * @param completionTokens The number of tokens
     */
    public void setCompletionTokens(int completionTokens) {
        this.completionTokens = completionTokens;
    }
    
    /**
     * Get the total number of tokens.
     * 
     * @return The number of tokens
     */
    public int getTotalTokens() {
        return totalTokens;
    }
    
    /**
     * Set the total number of tokens.
     * 
     * @param totalTokens The number of tokens
     */
    public void setTotalTokens(int totalTokens) {
        this.totalTokens = totalTokens;
    }
    
    /**
     * Get the reason the response was finished.
     * 
     * @return The finish reason
     */
    public String getFinishReason() {
        return finishReason;
    }
    
    /**
     * Set the reason the response was finished.
     * 
     * @param finishReason The finish reason
     */
    public void setFinishReason(String finishReason) {
        this.finishReason = finishReason;
        
        // Update the first candidate's finish reason if it exists
        if (this.candidates != null && !this.candidates.isEmpty()) {
            this.candidates.get(0).setFinishReason(finishReason);
        }
    }
    
    /**
     * Get the candidates in the response.
     * 
     * @return The candidates
     */
    public List<Candidate> getCandidates() {
        return candidates;
    }
    
    /**
     * Set the candidates in the response.
     * 
     * @param candidates The candidates
     */
    public void setCandidates(List<Candidate> candidates) {
        this.candidates = candidates;
    }
    
    /**
     * Add a candidate to the response.
     * 
     * @param candidate The candidate to add
     */
    public void addCandidate(Candidate candidate) {
        if (this.candidates == null) {
            this.candidates = new ArrayList<>();
        }
        this.candidates.add(candidate);
    }
    
    /**
     * Builder for LlmResponse.
     */
    public static class Builder {
        private String id;
        private String model;
        private String content;
        private List<ToolCall> toolCalls;
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
        private String finishReason;
        
        /**
         * Set the ID of the response.
         * 
         * @param id The ID
         * @return This builder
         */
        public Builder setId(String id) {
            this.id = id;
            return this;
        }
        
        /**
         * Set the model used to generate the response.
         * 
         * @param model The model
         * @return This builder
         */
        public Builder setModel(String model) {
            this.model = model;
            return this;
        }
        
        /**
         * Set the content of the response.
         * 
         * @param content The content
         * @return This builder
         */
        public Builder setContent(String content) {
            this.content = content;
            return this;
        }
        
        /**
         * Set the tool calls in the response.
         * 
         * @param toolCalls The tool calls
         * @return This builder
         */
        public Builder setToolCalls(List<ToolCall> toolCalls) {
            this.toolCalls = toolCalls;
            return this;
        }
        
        /**
         * Set the number of tokens in the prompt.
         * 
         * @param promptTokens The number of tokens
         * @return This builder
         */
        public Builder setPromptTokens(int promptTokens) {
            this.promptTokens = promptTokens;
            return this;
        }
        
        /**
         * Set the number of tokens in the completion.
         * 
         * @param completionTokens The number of tokens
         * @return This builder
         */
        public Builder setCompletionTokens(int completionTokens) {
            this.completionTokens = completionTokens;
            return this;
        }
        
        /**
         * Set the total number of tokens.
         * 
         * @param totalTokens The number of tokens
         * @return This builder
         */
        public Builder setTotalTokens(int totalTokens) {
            this.totalTokens = totalTokens;
            return this;
        }
        
        /**
         * Set the reason the response was finished.
         * 
         * @param finishReason The finish reason
         * @return This builder
         */
        public Builder setFinishReason(String finishReason) {
            this.finishReason = finishReason;
            return this;
        }
        
        /**
         * Build the LlmResponse.
         * 
         * @return The built LlmResponse
         */
        public LlmResponse build() {
            return new LlmResponse(
                    id,
                    model,
                    content,
                    toolCalls,
                    promptTokens,
                    completionTokens,
                    totalTokens,
                    finishReason);
        }
    }
    
    /**
     * Create a new builder.
     * 
     * @return A new builder
     */
    public static Builder builder() {
        return new Builder();
    }
}
