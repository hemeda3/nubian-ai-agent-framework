package com.nubian.ai.agentpress.model;

/**
 * Maps an XML node to a function parameter.
 */
public class XmlNodeMapping {
    private final String paramName;
    private final String nodeType;
    private final String path;
    private final boolean required;
    private final String valueType;
    
    /**
     * Create a new XML node mapping.
     * 
     * @param paramName Name of the function parameter
     * @param nodeType Type of node ("element", "attribute", "text", "content", or "xpath")
     * @param path XPath-like path to the node ("." means root element)
     * @param required Whether the parameter is required
     * @param valueType Type of value ("string", "int", "float", "boolean", "json")
     */
    public XmlNodeMapping(String paramName, String nodeType, String path, boolean required, String valueType) {
        this.paramName = paramName;
        this.nodeType = nodeType;
        this.path = path;
        this.required = required;
        this.valueType = valueType;
    }
    
    /**
     * Create a new XML node mapping with default values.
     * 
     * @param paramName Name of the function parameter
     */
    public XmlNodeMapping(String paramName) {
        this(paramName, "element", ".", true, "string");
    }
    
    /**
     * Create a new XML node mapping with default value type.
     * 
     * @param paramName Name of the function parameter
     * @param nodeType Type of node ("element", "attribute", "text", or "content")
     * @param path XPath-like path to the node ("." means root element)
     * @param required Whether the parameter is required
     */
    public XmlNodeMapping(String paramName, String nodeType, String path, boolean required) {
        this(paramName, nodeType, path, required, "string");
    }
    
    /**
     * Get the parameter name.
     * 
     * @return The parameter name
     */
    public String getParamName() {
        return paramName;
    }
    
    /**
     * Get the node type.
     * 
     * @return The node type
     */
    public String getNodeType() {
        return nodeType;
    }
    
    /**
     * Get the path.
     * 
     * @return The path
     */
    public String getPath() {
        return path;
    }
    
    /**
     * Check if the parameter is required.
     * 
     * @return true if required, false otherwise
     */
    public boolean isRequired() {
        return required;
    }
    
    /**
     * Get the value type.
     * 
     * @return The value type ("string", "int", "float", "boolean", "json")
     */
    public String getValueType() {
        return valueType;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        XmlNodeMapping that = (XmlNodeMapping) o;
        
        if (required != that.required) return false;
        if (paramName != null ? !paramName.equals(that.paramName) : that.paramName != null) return false;
        if (nodeType != null ? !nodeType.equals(that.nodeType) : that.nodeType != null) return false;
        if (path != null ? !path.equals(that.path) : that.path != null) return false;
        return valueType != null ? valueType.equals(that.valueType) : that.valueType == null;
    }
    
    @Override
    public int hashCode() {
        int result = paramName != null ? paramName.hashCode() : 0;
        result = 31 * result + (nodeType != null ? nodeType.hashCode() : 0);
        result = 31 * result + (path != null ? path.hashCode() : 0);
        result = 31 * result + (required ? 1 : 0);
        result = 31 * result + (valueType != null ? valueType.hashCode() : 0);
        return result;
    }
    
    @Override
    public String toString() {
        return "XmlNodeMapping{" +
                "paramName='" + paramName + '\'' +
                ", nodeType='" + nodeType + '\'' +
                ", path='" + path + '\'' +
                ", required=" + required +
                '}';
    }
}
