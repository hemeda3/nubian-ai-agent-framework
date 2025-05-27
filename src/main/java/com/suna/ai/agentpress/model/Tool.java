package com.Nubian.ai.agentpress.model;

import com.Nubian.ai.agentpress.annotations.ToolFunction;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for all tools.
 * 
 * Provides the foundation for implementing tools with schema registration
 * and result handling capabilities.
 */
public abstract class Tool {
    private static final Logger logger = LoggerFactory.getLogger(Tool.class);
    
    private final Map<String, List<ToolSchema>> schemas = new ConcurrentHashMap<>();
    protected ObjectMapper objectMapper; 
    
    public Tool() {
        logger.debug("Initializing tool class: {}", this.getClass().getSimpleName());
        registerSchemas();
    }
    
    public Tool(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        logger.debug("Initializing tool class: {} with ObjectMapper", this.getClass().getSimpleName());
        registerSchemas();
    }

    private String sanitizeOpenAiName(String nameInput, String contextMethodName, String prefixIfGenerated) {
        String baseName = nameInput;
        if (baseName == null || baseName.trim().isEmpty()) {
            logger.warn("Name for sanitization is null or empty for context method '{}'. Using Java method name as base.", contextMethodName);
            baseName = contextMethodName; 
            if (baseName == null || baseName.trim().isEmpty()) { 
                 String generated = prefixIfGenerated + UUID.randomUUID().toString().substring(0, 8).replace("-", "");
                 logger.error("CRITICAL: Base name for sanitization (method '{}') also null/empty. Using generated: {}", contextMethodName, generated);
                 return generated; // Already short and valid
            }
        }
    
        String sanitized = baseName.trim();
        sanitized = sanitized.replaceAll("\\s+", "_"); 
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9_-]", ""); 
    
        if (sanitized.isEmpty()) {
            logger.warn("Sanitization of name '{}' (for method '{}') resulted in empty string. Generating a new name.", baseName, contextMethodName);
            String contextPart = contextMethodName.replaceAll("[^a-zA-Z0-9_-]", "");
            if (contextPart.length() > 20) contextPart = contextPart.substring(0, 20);
            sanitized = prefixIfGenerated + contextPart + "_" + UUID.randomUUID().toString().substring(0, 8).replace("-", "");
            // Re-sanitize generated name
            sanitized = sanitized.replaceAll("\\s+", "_"); 
            sanitized = sanitized.replaceAll("[^a-zA-Z0-9_-]", "");
        }

        if (sanitized.length() > 64) {
            sanitized = sanitized.substring(0, 64);
        }
        
        if (sanitized.isEmpty()){ // Final fallback
            String finalGenerated = (prefixIfGenerated.replaceAll("[^a-zA-Z0-9_-]", "") + 
                                     "gen_" + 
                                     UUID.randomUUID().toString().substring(0,8).replace("-", ""));
            logger.error("CRITICAL: Sanitized name is still empty after all fallbacks for base '{}', method '{}'. Using final generated: {}", baseName, contextMethodName, finalGenerated);
            sanitized = finalGenerated;
            if (sanitized.length() > 64) sanitized = sanitized.substring(0, 64);
            if (sanitized.isEmpty()) sanitized = "critical_tool_name"; 
        }

        return sanitized;
    }
    
    private void registerSchemas() {
        for (Method method : this.getClass().getMethods()) {
            if (method.isAnnotationPresent(ToolFunction.class)) {
                ToolFunction annotation = method.getAnnotation(ToolFunction.class);
                String registrationKey = annotation.name().isEmpty() ? method.getName() : annotation.name();
                String nameForOpenAI = sanitizeOpenAiName(registrationKey, method.getName(), "toolfunc_");

                SchemaType schemaType = annotation.schemaType();
                Map<String, Object> schemaMap = new HashMap<>();
                schemaMap.put("name", nameForOpenAI); 
                schemaMap.put("description", annotation.description());
                
                if (schemaType == SchemaType.XML && !annotation.xmlTagName().isEmpty()) {
                    String rawTagName = annotation.xmlTagName();
                    String sanitizedTagName = sanitizeOpenAiName(rawTagName, method.getName() + "_tag", "xmltag_");
                    
                    XmlTagSchema xmlTagSchema = new XmlTagSchema(sanitizedTagName);
                    if (!annotation.xmlExample().isEmpty()) {
                        xmlTagSchema.setExample(annotation.xmlExample());
                        schemaMap.put("example", annotation.xmlExample());
                    }
                    
                    ToolSchema schema = new ToolSchema(SchemaType.XML, schemaMap, xmlTagSchema);
                    addSchema(registrationKey, schema);

                    if (!nameForOpenAI.equals(sanitizedTagName) && !sanitizedTagName.isEmpty()) {
                        Map<String, Object> aliasXmlSchemaMap = new HashMap<>(schemaMap);
                        aliasXmlSchemaMap.put("name", sanitizedTagName); 
                        aliasXmlSchemaMap.put("description", annotation.description() + " (Alias for XML tag: " + sanitizedTagName + ")");
                        ToolSchema aliasSchema = new ToolSchema(SchemaType.XML, aliasXmlSchemaMap, xmlTagSchema);
                        addSchema(sanitizedTagName, aliasSchema); // Register alias by its sanitized tag name
                        logger.debug("Registered XML alias for @ToolFunction: {} (OpenAI name) for key {}", sanitizedTagName, sanitizedTagName);
                    }
                } else {
                    ToolSchema schema = new ToolSchema(schemaType, schemaMap);
                    addSchema(registrationKey, schema);
                }
                continue; // Processed by @ToolFunction, skip other annotations for this method
            }
            
            if (method.isAnnotationPresent(OpenApiSchema.class)) {
                OpenApiSchema annotation = method.getAnnotation(OpenApiSchema.class);
                String schemaJson = annotation.value();
                String registrationKey = method.getName(); 

                Map<String, Object> schemaMap = new HashMap<>();
                String nameCandidateForOpenAI = registrationKey; 

                try {
                    if (objectMapper != null) {
                        // Ensure schemaMap is mutable
                        schemaMap = new HashMap<>(objectMapper.readValue(schemaJson, Map.class));
                        Object nameFromJsonObj = schemaMap.get("name");
                        if (nameFromJsonObj instanceof String && !((String) nameFromJsonObj).trim().isEmpty()) {
                            nameCandidateForOpenAI = ((String) nameFromJsonObj).trim();
                        } else {
                            logger.debug("OpenAPI schema for method '{}' had invalid/empty 'name' (was: '{}'). Using registration key name '{}' as candidate.", registrationKey, nameFromJsonObj, registrationKey);
                        }
                    } else {
                        logger.warn("ObjectMapper is null for OpenAPI schema of method {}. Using registration key name '{}' as candidate.", registrationKey, registrationKey);
                        schemaMap.put("parameters", Map.of("type", "object", "properties", Map.of("raw_schema_note", Map.of("type", "string", "description", "Schema could not be parsed, see raw_schema_json_string"))));
                        schemaMap.put("raw_schema_json_string", schemaJson); 
                    }
                } catch (JsonProcessingException e) {
                    logger.error("Failed to parse OpenAPI schema JSON for method {}: {}. Using registration key name '{}' as candidate.", registrationKey, e.getMessage(), registrationKey);
                    schemaMap.put("parameters", Map.of("type", "object", "properties", Map.of("parse_error_note", Map.of("type", "string", "description", "Schema JSON parsing failed, see raw_schema_json_string"))));
                    schemaMap.put("raw_schema_json_string", schemaJson);
                }

                String finalNameForOpenAI = sanitizeOpenAiName(nameCandidateForOpenAI, registrationKey, "openapi_");
                schemaMap.put("name", finalNameForOpenAI);
                
                Object descFromJsonObj = schemaMap.get("description");
                String descFromJson = (descFromJsonObj == null) ? null : descFromJsonObj.toString().trim();
                if (descFromJson == null || descFromJson.isEmpty()) {
                    schemaMap.put("description", "OpenAPI tool: " + finalNameForOpenAI);
                }
                
                if (!schemaMap.containsKey("parameters")) {
                    schemaMap.put("parameters", Map.of("type", "object", "properties", new HashMap<>()));
                }

                ToolSchema toolSchema = new ToolSchema(SchemaType.OPENAPI, schemaMap);
                addSchema(registrationKey, toolSchema);
                continue; 
            }
            
            if (method.isAnnotationPresent(XmlSchema.class)) {
                XmlSchema annotation = method.getAnnotation(XmlSchema.class);
                String registrationKey = method.getName();
                String nameForOpenAI = sanitizeOpenAiName(registrationKey, registrationKey, "xmlfunc_");

                Map<String, Object> schemaMap = new HashMap<>();
                schemaMap.put("name", nameForOpenAI);
                
                String rawTagName = annotation.tagName();
                String sanitizedTagName = sanitizeOpenAiName(rawTagName, registrationKey + "_tag", "xmltag_");
                
                schemaMap.put("description", "XML tool using tag: " + sanitizedTagName);
                schemaMap.put("example", annotation.example());

                XmlTagSchema xmlTagSchema = new XmlTagSchema(sanitizedTagName);
                if (!annotation.example().isEmpty()) {
                    xmlTagSchema.setExample(annotation.example());
                }
                XmlSchema.XmlMapping[] xmlMappings = annotation.mappings();
                if (xmlMappings != null && xmlMappings.length > 0) {
                    for (XmlSchema.XmlMapping mapping : xmlMappings) {
                        xmlTagSchema.addMapping(new XmlNodeMapping(mapping.paramName(), mapping.nodeType(), mapping.path(), mapping.required(), mapping.valueType()));
                    }
                }
                
                ToolSchema schema = new ToolSchema(SchemaType.XML, schemaMap, xmlTagSchema);
                addSchema(registrationKey, schema); 

                if (!nameForOpenAI.equals(sanitizedTagName) && !sanitizedTagName.isEmpty()) {
                    Map<String, Object> aliasSchemaMap = new HashMap<>(schemaMap);
                    aliasSchemaMap.put("name", sanitizedTagName); 
                    aliasSchemaMap.put("description", "XML tool alias using tag: " + sanitizedTagName);
                    ToolSchema aliasSchema = new ToolSchema(SchemaType.XML, aliasSchemaMap, xmlTagSchema);
                    addSchema(sanitizedTagName, aliasSchema); 
                }
                continue;
            }
            
            if (method.isAnnotationPresent(CustomSchema.class)) {
                CustomSchema annotation = method.getAnnotation(CustomSchema.class);
                String registrationKey = method.getName();
                String nameForOpenAI = sanitizeOpenAiName(registrationKey, registrationKey, "customfunc_");
                
                Map<String, Object> schemaMap = new HashMap<>();
                schemaMap.put("name", nameForOpenAI);
                schemaMap.put("description", "Custom tool: " + nameForOpenAI);
                
                try {
                    if (objectMapper != null && annotation.value() != null && !annotation.value().trim().isEmpty()) {
                        Map<String, Object> customParams = objectMapper.readValue(annotation.value(), Map.class);
                        schemaMap.put("parameters", customParams);
                    } else {
                         schemaMap.put("parameters_json_string", annotation.value());
                         schemaMap.put("parameters", Map.of("type", "object", "properties", new HashMap<>())); // Default empty
                    }
                } catch (JsonProcessingException e) {
                    logger.warn("Could not parse CustomSchema value as JSON object for params for method {}. Storing as string.", registrationKey, e);
                    schemaMap.put("parameters_json_string", annotation.value());
                    schemaMap.put("parameters", Map.of("type", "object", "properties", Map.of("custom_schema_note", Map.of("type", "string", "description", "See parameters_json_string"))));
                }
                 if (!schemaMap.containsKey("parameters")) {
                    schemaMap.put("parameters", Map.of("type", "object", "properties", new HashMap<>()));
                }
                
                ToolSchema schema = new ToolSchema(SchemaType.CUSTOM, schemaMap);
                addSchema(registrationKey, schema);
                // No continue needed as it's the last check
            }
        }
    }
    
    public Map<String, List<ToolSchema>> getSchemas() {
        return new HashMap<>(schemas);
    }
    
    protected ToolResult successResponse(Object data) {
        logger.debug("Created success response for {}", this.getClass().getSimpleName());
        return new ToolResult(true, data);
    }
    
    protected ToolResult failResponse(String message) {
        logger.debug("Tool {} returned failed result: {}", this.getClass().getSimpleName(), message);
        return new ToolResult(false, message);
    }
    
    protected void addSchema(String methodName, ToolSchema schema) {
        // The methodName key here is the registration key (Java-side identifier)
        // It should NOT be sanitized here if ToolRegistry uses the original names for lookup.
        // The schema object itself contains the sanitized name for OpenAI.
        schemas.computeIfAbsent(methodName, k -> new ArrayList<>()).add(schema);
        logger.debug("Added {} schema for registration key '{}' (OpenAI func name: '{}')", 
            schema.getSchemaType(), methodName, schema.getSchema() != null ? schema.getSchema().get("name") : "N/A");
    }
}
