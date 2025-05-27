package com.Nubian.ai.agentpress.model;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Schema definition for XML tool tags.
 */
public class XmlTagSchema {
    private static final Logger logger = LoggerFactory.getLogger(XmlTagSchema.class);
    
    private final String tagName;
    private final List<XmlNodeMapping> mappings;
    private String example;
    
    /**
     * Create a new XML tag schema.
     * 
     * @param tagName Root tag name for the tool
     * @param mappings Parameter mappings for the tag
     * @param example Example showing tag usage
     */
    public XmlTagSchema(String tagName, List<XmlNodeMapping> mappings, String example) {
        this.tagName = tagName;
        this.mappings = new ArrayList<>(mappings);
        this.example = example;
    }
    
    /**
     * Create a new XML tag schema without mappings or example.
     * 
     * @param tagName Root tag name for the tool
     */
    public XmlTagSchema(String tagName) {
        this(tagName, new ArrayList<>(), null);
    }
    
    /**
     * Add a new node mapping to the schema.
     * 
     * @param paramName Name of the function parameter
     * @param nodeType Type of node ("element", "attribute", "text", or "content")
     * @param path XPath-like path to the node
     * @param required Whether the parameter is required
     */
    public void addMapping(String paramName, String nodeType, String path, boolean required) {
        mappings.add(new XmlNodeMapping(paramName, nodeType, path, required));
        logger.debug("Added XML mapping for parameter '{}' with type '{}' at path '{}', required={}",
                paramName, nodeType, path, required);
    }
    
    /**
     * Add a new node mapping to the schema with default values.
     * 
     * @param paramName Name of the function parameter
     */
    public void addMapping(String paramName) {
        addMapping(paramName, "element", ".", true);
    }
    
    /**
     * Add a pre-created node mapping to the schema.
     * 
     * @param mapping The XML node mapping to add
     */
    public void addMapping(XmlNodeMapping mapping) {
        if (mapping != null) {
            mappings.add(mapping);
            logger.debug("Added XML mapping for parameter '{}' with type '{}' at path '{}', required={}",
                    mapping.getParamName(), mapping.getNodeType(), mapping.getPath(), mapping.isRequired());
        }
    }
    
    /**
     * Get the tag name.
     * 
     * @return The tag name
     */
    public String getTagName() {
        return tagName;
    }
    
    /**
     * Get the parameter mappings.
     * 
     * @return The parameter mappings
     */
    public List<XmlNodeMapping> getMappings() {
        return new ArrayList<>(mappings);
    }
    
    /**
     * Get the example.
     * 
     * @return The example, or null if not set
     */
    public String getExample() {
        return example;
    }
    
    /**
     * Set the example.
     * 
     * @param example The example
     */
    public void setExample(String example) {
        this.example = example;
    }
}
