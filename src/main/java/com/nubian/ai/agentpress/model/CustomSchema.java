package com.nubian.ai.agentpress.model;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for custom schema tools.
 * 
 * This annotation is used to define a custom schema for a tool method.
 * The schema can be used for specialized tool definitions.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CustomSchema {
    /**
     * The custom schema as a JSON string.
     * 
     * @return The schema
     */
    String value();
}
