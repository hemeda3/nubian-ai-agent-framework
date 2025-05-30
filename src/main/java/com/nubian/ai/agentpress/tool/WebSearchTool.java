package com.nubian.ai.agentpress.tool;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired; // Import Autowired
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper; // Import ObjectMapper
import com.nubian.ai.agentpress.model.OpenApiSchema;
import com.nubian.ai.agentpress.model.Tool;
import com.nubian.ai.agentpress.model.ToolResult;
import com.nubian.ai.agentpress.model.XmlSchema;

/**
 * A tool for performing web searches.
 */
@Component
public class WebSearchTool extends Tool {
    private static final Logger logger = LoggerFactory.getLogger(WebSearchTool.class);

    /**
     * Constructs a WebSearchTool with an ObjectMapper.
     * @param objectMapper The ObjectMapper to use for JSON processing.
     */
    @Autowired
    public WebSearchTool(ObjectMapper objectMapper) {
        super(objectMapper); // Pass the objectMapper to the parent Tool class
        logger.debug("WebSearchTool initialized with ObjectMapper.");
    }
    
    /**
     * Perform a web search.
     * 
     * @param query The search query
     * @return The search results
     */
    @OpenApiSchema("""
    {
        "name": "search",
        "description": "Search the web for information",
        "parameters": {
            "type": "object",
            "properties": {
                "query": {
                    "type": "string",
                    "description": "The search query"
                }
            },
            "required": ["query"]
        }
    }
    """)
    @XmlSchema(
        tagName = "search",
        mappings = {
            @XmlSchema.XmlMapping(
                paramName = "query",
                nodeType = "content"
            )
        },
        example = """
        <search>What is the capital of France?</search>
        """
    )
    public CompletableFuture<ToolResult> search(String query) {
        logger.info("Performing web search for: {}", query);
        
        // This should call a real search API
        return CompletableFuture.supplyAsync(() -> {
            if ("What is the capital of France?".equalsIgnoreCase(query)) {
                return successResponse("Paris is the capital of France.");
            }
            return successResponse("Dummy search result for query: " + query);
        });
    }
    
    /**
     * Get detailed information about a topic.
     * 
     * @param topic The topic to get information about
     * @param maxResults The maximum number of results to return
     * @return Detailed information about the topic
     */
    @OpenApiSchema("""
    {
        "name": "getInfo",
        "description": "Get detailed information about a topic",
        "parameters": {
            "type": "object",
            "properties": {
                "topic": {
                    "type": "string",
                    "description": "The topic to get information about"
                },
                "maxResults": {
                    "type": "integer",
                    "description": "The maximum number of results to return",
                    "default": 3
                }
            },
            "required": ["topic"]
        }
    }
    """)
    @XmlSchema(
        tagName = "get-info",
        mappings = {
            @XmlSchema.XmlMapping(
                paramName = "topic",
                nodeType = "element",
                path = "topic"
            ),
            @XmlSchema.XmlMapping(
                paramName = "maxResults",
                nodeType = "attribute",
                required = false
            )
        },
        example = """
        <get-info maxResults="5">
            <topic>Spring Boot</topic>
        </get-info>
        """
    )
    public CompletableFuture<ToolResult> getInfo(Map<String, Object> params) {
        String topic = (String) params.get("topic");
        int maxResults = params.containsKey("maxResults") ? 
                Integer.parseInt(params.get("maxResults").toString()) : 3;
        
        logger.info("Getting info about: {} (max results: {})", topic, maxResults);
        
        // This should call a real information API
        return CompletableFuture.supplyAsync(() -> {
            if ("France".equalsIgnoreCase(topic)) {
                return successResponse("France is a country in Western Europe. Its capital is Paris. Max results: " + maxResults);
            }
            return successResponse("Dummy information for topic: " + topic + ", max results: " + maxResults);
        });
    }
}
