package com.Nubian.ai.agent.tool.providers;

import java.util.Map;

/**
 * Interface for data providers that provide access to external data sources.
 */
public interface DataProvider {
    
    /**
     * Get the available endpoints for this provider.
     * 
     * @return Map of endpoint names to their descriptions/schemas
     */
    Map<String, Object> getEndpoints();
    
    /**
     * Call a specific endpoint with the given payload.
     * 
     * @param endpoint The endpoint name to call
     * @param payload The payload to send with the request
     * @return The result from the endpoint
     */
    Object callEndpoint(String endpoint, Map<String, Object> payload);
}
