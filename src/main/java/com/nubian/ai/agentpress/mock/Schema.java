package com.nubian.ai.agentpress.mock;

import java.util.List;
import java.util.Map;

/**
 * Mock implementation of the Schema class.
 */
public class Schema {
    private String type;
    private String description;
    private Map<String, Schema> properties;
    private List<String> required;
    private Schema items;
    private List<Object> enumValues;
    
    private Schema(Builder builder) {
        this.type = builder.type;
        this.description = builder.description;
        this.properties = builder.properties;
        this.required = builder.required;
        this.items = builder.items;
        this.enumValues = builder.enumValues;
    }
    
    public String getType() {
        return type;
    }
    
    public String getDescription() {
        return description;
    }
    
    public Map<String, Schema> getProperties() {
        return properties;
    }
    
    public List<String> getRequired() {
        return required;
    }
    
    public Schema getItems() {
        return items;
    }
    
    public List<Object> getEnum() {
        return enumValues;
    }
    
    /**
     * Builder for Schema.
     */
    public static class Builder {
        private String type;
        private String description;
        private Map<String, Schema> properties;
        private List<String> required;
        private Schema items;
        private List<Object> enumValues;
        
        public Builder setType(String type) {
            this.type = type;
            return this;
        }
        
        public Builder setDescription(String description) {
            this.description = description;
            return this;
        }
        
        public Builder setProperties(Map<String, Schema> properties) {
            this.properties = properties;
            return this;
        }
        
        public Builder setRequired(List<String> required) {
            this.required = required;
            return this;
        }
        
        public Builder setItems(Schema items) {
            this.items = items;
            return this;
        }
        
        public Builder setEnum(List<Object> enumValues) {
            this.enumValues = enumValues;
            return this;
        }
        
        public Schema build() {
            return new Schema(this);
        }
    }
    
    /**
     * Create a new builder.
     */
    public static Builder builder() {
        return new Builder();
    }
}
