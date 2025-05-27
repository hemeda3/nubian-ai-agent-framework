package com.Nubian.ai.agent.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.Nubian.ai.agent.tool.providers.*;
import com.Nubian.ai.agentpress.annotations.ToolFunction;
import com.Nubian.ai.agentpress.model.SchemaType;
import com.Nubian.ai.agentpress.model.Tool;
import com.Nubian.ai.agentpress.model.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * Tool for making requests to various data providers.
 */
@Component
@Slf4j
public class DataProviderTool extends Tool {
    private final Map<String, DataProvider> registeredProviders = new HashMap<>();
    private final ObjectMapper objectMapper;
    
    @Value("${agent.tool.data.rapid-api-key:}")
    private String rapidApiKey;
    
    @Autowired
    public DataProviderTool(ObjectMapper objectMapper) {
        super(objectMapper); // Pass the injected ObjectMapper to the superclass
        this.objectMapper = objectMapper; // Keep this for local use if needed, though superclass handles it
    }
    
    @PostConstruct
    public void init() {
        // Only register providers if API key is available
        if (rapidApiKey != null && !rapidApiKey.isEmpty()) {
            registerProviders();
        } else {
            log.warn("Rapid API key not found. Data provider tools will be limited.");
        }
    }
    
    /**
     * Register all available data providers.
     */
    private void registerProviders() {
        // Register all real provider implementations
        registeredProviders.put("linkedin", new LinkedinProvider(rapidApiKey));
        registeredProviders.put("yahoo_finance", new YahooFinanceProvider(rapidApiKey));
        registeredProviders.put("amazon", new AmazonProvider(rapidApiKey));
        registeredProviders.put("zillow", new ZillowProvider(rapidApiKey));
        registeredProviders.put("twitter", new TwitterProvider(rapidApiKey));
        
        log.info("Registered {} data providers: {}", registeredProviders.size(), registeredProviders.keySet());
    }
    
    /**
     * Get available endpoints for a specific data provider.
     * 
     * @param serviceName The name of the data provider (e.g., 'linkedin')
     * @return ToolResult containing the available endpoints
     */
    @ToolFunction(
        name = "get-data-provider-endpoints",
        description = "Get available endpoints for a specific data provider",
        schemaType = SchemaType.XML,
        xmlTagName = "get-data-provider-endpoints",
        xmlExample = "<get-data-provider-endpoints service_name=\"linkedin\">\n</get-data-provider-endpoints>"
    )
    public ToolResult getDataProviderEndpoints(String serviceName) {
        try {
            log.info("Getting endpoints for data provider: {}", serviceName);
            
            if (serviceName == null || serviceName.isEmpty()) {
                return failResponse("Data provider name is required.");
            }
            
            if (!registeredProviders.containsKey(serviceName)) {
                return failResponse(String.format(
                    "Data provider '%s' not found. Available data providers: %s", 
                    serviceName, 
                    registeredProviders.keySet()
                ));
            }
            
            Map<String, Object> endpoints = registeredProviders.get(serviceName).getEndpoints();
            return successResponse(endpoints);
        } catch (Exception e) {
            log.error("Error getting data provider endpoints: {}", e.getMessage(), e);
            String errorMessage = e.getMessage();
            if (errorMessage.length() > 200) {
                errorMessage = errorMessage.substring(0, 200) + "...";
            }
            return failResponse("Error getting data provider endpoints: " + errorMessage);
        }
    }
    
    /**
     * Execute a call to a specific data provider endpoint.
     * 
     * @param serviceName The name of the data provider (e.g., 'linkedin')
     * @param route The key of the endpoint to call
     * @param payload The payload to send with the data provider call
     * @return ToolResult containing the result of the API call
     */
    @ToolFunction(
        name = "execute-data-provider-call",
        description = "Execute a call to a specific data provider endpoint",
        schemaType = SchemaType.XML,
        xmlTagName = "execute-data-provider-call",
        xmlExample = "<execute-data-provider-call service_name=\"linkedin\" route=\"person\">\n"
                + "    {\"link\": \"https://www.linkedin.com/in/johndoe/\"}\n"
                + "</execute-data-provider-call>"
    )
    public ToolResult executeDataProviderCall(String serviceName, String route, String payload) {
        try {
            log.info("Executing call to data provider: {}, route: {}", serviceName, route);
            
            if (serviceName == null || serviceName.isEmpty()) {
                return failResponse("Service name is required.");
            }
            
            if (route == null || route.isEmpty()) {
                return failResponse("Route is required.");
            }
            
            if (!registeredProviders.containsKey(serviceName)) {
                return failResponse(String.format(
                    "Data provider '%s' not found. Available data providers: %s", 
                    serviceName, 
                    registeredProviders.keySet()
                ));
            }
            
            if (route.equals(serviceName)) {
                return failResponse(String.format(
                    "Route '%s' is the same as service name '%s'. Please use a valid endpoint.",
                    route, 
                    serviceName
                ));
            }
            
            // Parse payload string to Map
            Map<String, Object> payloadMap;
            try {
                payloadMap = objectMapper.readValue(payload, Map.class);
            } catch (JsonProcessingException e) {
                return failResponse("Invalid JSON payload: " + e.getMessage());
            }
            
            DataProvider provider = registeredProviders.get(serviceName);
            if (!provider.getEndpoints().containsKey(route)) {
                return failResponse(String.format(
                    "Endpoint '%s' not found in %s data provider.",
                    route, 
                    serviceName
                ));
            }
            
            Object result = provider.callEndpoint(route, payloadMap);
            return successResponse(result);
        } catch (Exception e) {
            log.error("Error executing data provider call: {}", e.getMessage(), e);
            String errorMessage = e.getMessage();
            if (errorMessage.length() > 200) {
                errorMessage = errorMessage.substring(0, 200) + "...";
            }
            return failResponse("Error executing data provider call: " + errorMessage);
        }
    }
    
    // Mock provider class has been removed as all providers are now implemented with real classes
}
