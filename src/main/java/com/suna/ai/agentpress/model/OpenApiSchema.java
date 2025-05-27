package com.Nubian.ai.agentpress.model;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for OpenAPI schema tools.
 * 
 * This annotation is used to define an OpenAPI schema for a tool method.
 * The schema is used to generate the function calling interface for the LLM.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OpenApiSchema {
    /**
     * The OpenAPI schema as a JSON string.
     * 
     * @return The schema
     */
    String value();
}
