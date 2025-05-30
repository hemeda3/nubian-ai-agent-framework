package com.nubian.ai.agent.tool.providers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Abstract base class for data providers.
 * Provides common functionality for API calls and endpoint management.
 */
@Slf4j
public abstract class AbstractDataProvider implements DataProvider {
    protected final String apiKey;
    protected final RestTemplate restTemplate;
    protected final Map<String, Object> endpoints = new HashMap<>();
    
    /**
     * Create a new data provider with the specified API key.
     * 
     * @param apiKey The API key for accessing the provider's services
     */
    public AbstractDataProvider(String apiKey) {
        this.apiKey = apiKey;
        this.restTemplate = new RestTemplate();
        registerEndpoints();
    }
    
    /**
     * Register the available endpoints for this provider.
     * Should be implemented by subclasses to define their specific endpoints.
     */
    protected abstract void registerEndpoints();
    
    @Override
    public Map<String, Object> getEndpoints() {
        return Collections.unmodifiableMap(endpoints);
    }
    
    /**
     * Execute a HTTP request to the specified URL.
     * 
     * @param url The URL to call
     * @param method The HTTP method to use
     * @param headers The HTTP headers to include
     * @param body The request body (optional)
     * @param responseType The expected response type
     * @return The response body
     */
    protected <T> T executeRequest(
            String url, 
            HttpMethod method, 
            HttpHeaders headers, 
            Object body, 
            Class<T> responseType) {
        
        try {
            HttpEntity<?> requestEntity = new HttpEntity<>(body, headers);
            
            log.info("Executing request to {}", url);
            ResponseEntity<T> response = restTemplate.exchange(
                    url,
                    method,
                    requestEntity,
                    responseType);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                return response.getBody();
            } else {
                throw new RuntimeException("Request failed with status: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error executing request to {}: {}", url, e.getMessage(), e);
            throw new RuntimeException("Error executing request: " + e.getMessage(), e);
        }
    }
    
    /**
     * Add an endpoint to this provider's registry.
     * 
     * @param name The endpoint name
     * @param description The endpoint description
     * @param parameters The endpoint parameters
     */
    protected void addEndpoint(String name, String description, Map<String, Object> parameters) {
        Map<String, Object> endpoint = new HashMap<>();
        endpoint.put("description", description);
        endpoint.put("parameters", parameters);
        
        endpoints.put(name, endpoint);
    }
}
