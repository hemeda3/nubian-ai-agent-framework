package com.nubian.ai.agentpress.model;

import java.util.Map;

/**
 * Container for tool schemas with type information.
 */
public class ToolSchema {
    private final SchemaType schemaType;
    private final Map<String, Object> schema;
    private final XmlTagSchema xmlSchema;
    
    /**
     * Create a new tool schema.
     * 
     * @param schemaType Type of schema (OpenAPI, XML, or Custom)
     * @param schema The actual schema definition
     * @param xmlSchema XML-specific schema if applicable
     */
    public ToolSchema(SchemaType schemaType, Map<String, Object> schema, XmlTagSchema xmlSchema) {
        this.schemaType = schemaType;
        this.schema = schema;
        this.xmlSchema = xmlSchema;
    }
    
    /**
     * Create a new tool schema without XML schema.
     * 
     * @param schemaType Type of schema (OpenAPI, XML, or Custom)
     * @param schema The actual schema definition
     */
    public ToolSchema(SchemaType schemaType, Map<String, Object> schema) {
        this(schemaType, schema, null);
    }
    
    /**
     * Get the schema type.
     * 
     * @return The schema type
     */
    public SchemaType getSchemaType() {
        return schemaType;
    }
    
    /**
     * Get the schema definition.
     * 
     * @return The schema definition
     */
    public Map<String, Object> getSchema() {
        return schema;
    }
    
    /**
     * Get the XML schema.
     * 
     * @return The XML schema, or null if not applicable
     */
    public XmlTagSchema getXmlSchema() {
        return xmlSchema;
    }
}
