package com.nubian.ai.agentpress.model;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for XML schema tools.
 * 
 * This annotation is used to define an XML schema for a tool method.
 * The schema is used to generate the XML tool calling interface for the LLM.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface XmlSchema {
    /**
     * The root tag name for the tool.
     * 
     * @return The tag name
     */
    String tagName();
    
    /**
     * Example showing tag usage.
     * 
     * @return The example
     */
    String example() default "";
    
    /**
     * Parameter mappings for the tag.
     * 
     * @return The mappings
     */
    XmlMapping[] mappings() default {};
    
    /**
     * Inner annotation for XML node mappings.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({})
    public @interface XmlMapping {
        /**
         * Name of the function parameter.
         * 
         * @return The parameter name
         */
        String paramName();
        
        /**
         * Type of node ("element", "attribute", "text", or "content").
         * 
         * @return The node type
         */
        String nodeType() default "element";
        
        /**
         * XPath-like path to the node ("." means root element).
         * 
         * @return The path
         */
        String path() default ".";
        
        /**
         * Whether the parameter is required.
         * 
         * @return true if required, false otherwise
         */
        boolean required() default true;
        
        /**
         * The target Java type for the value ("string", "int", "boolean", "json", etc.).
         * 
         * @return The value type
         */
        String valueType() default "string";
    }
}
