package com.nubian.ai.agentpress.mock;

import java.util.ArrayList;
import java.util.List;

/**
 * Mock implementation of the Content class.
 */
public class Content {
    private String role;
    private List<Part> parts;
    
    private Content(Builder builder) {
        this.role = builder.role;
        this.parts = builder.parts;
    }
    
    public String getRole() {
        return role;
    }
    
    public List<Part> getParts() {
        return parts;
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
            return new Content(this);
        }
    }
    
    /**
     * Create a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }
}
