package com.Nubian.ai.agentpress.annotations;

import com.Nubian.ai.agentpress.model.SchemaType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for marking methods as tool functions.
 * Used to automatically generate tool schemas and provide metadata.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ToolFunction {
    /**
     * Name of the function. If not specified, method name will be used.
     */
    String name() default "";
    
    /**
     * Description of what the function does.
     */
    String description() default "";
    
    /**
     * Type of schema to use for this function.
     */
    SchemaType schemaType() default SchemaType.OPENAPI;
    
    /**
     * XML tag name, only used when schemaType is XML.
     */
    String xmlTagName() default "";
    
    /**
     * XML example, only used when schemaType is XML.
     */
    String xmlExample() default "";
}
