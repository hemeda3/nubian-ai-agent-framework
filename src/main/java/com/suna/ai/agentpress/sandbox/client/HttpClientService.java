package com.Nubian.ai.agentpress.sandbox.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference; // Added import

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service for making HTTP requests to the Daytona API.
 * 
 * TODO: Implement robust error handling and resilience for HTTP calls,
 * including retries, circuit breakers, and specific exception types.
 */
@Service
public class HttpClientService {
    private static final Logger logger = LoggerFactory.getLogger(HttpClientService.class);
    private static final int TIMEOUT_SECONDS = 30;
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;
    private final String defaultOrganizationId;

    public HttpClientService(
            ObjectMapper objectMapper,
            @Value("${daytona.api.base-url:http://localhost:8080/api/v1}") String baseUrl,
            @Value("${daytona.api.key:}") String apiKey,
            @Value("${daytona.organization.id:}") String defaultOrganizationId) {
        
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .build();
        
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.defaultOrganizationId = defaultOrganizationId;
        
        logger.info("HttpClientService initialized with base URL: {}", baseUrl);
    }

    /**
     * Add authentication headers to a request builder.
     */
    private HttpRequest.Builder addAuthHeaders(HttpRequest.Builder requestBuilder, String organizationId) {
        String orgIdToUse = (organizationId != null && !organizationId.isEmpty()) 
                            ? organizationId 
                            : this.defaultOrganizationId;
        
        if (this.apiKey != null && !this.apiKey.isEmpty()) {
            requestBuilder = requestBuilder.header("Authorization", "Bearer " + this.apiKey);
        }
        
        if (orgIdToUse != null && !orgIdToUse.isEmpty()) {
            requestBuilder = requestBuilder.header("X-Daytona-Organization-ID", orgIdToUse);
        }
        
        return requestBuilder;
    }

    /**
     * Make a GET request.
     */
    public <T> CompletableFuture<T> get(String path, Class<T> responseType, String organizationId) {
        String url = baseUrl + path;
        logger.debug("Making GET request to: {}", url);
        
        HttpRequest request = addAuthHeaders(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .GET(), organizationId)
                .build();
        
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> handleResponse(response, responseType));
    }

    /**
     * Make a GET request with a TypeReference for generic types.
     */
    public <T> CompletableFuture<T> get(String path, TypeReference<T> responseTypeRef, String organizationId) {
        String url = baseUrl + path;
        logger.debug("Making GET request to: {}", url);
        
        HttpRequest request = addAuthHeaders(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .GET(), organizationId)
                .build();
        
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> handleResponse(response, responseTypeRef));
    }

    /**
     * Make a POST request with a body.
     */
    public <T, R> CompletableFuture<R> post(String path, T body, Class<R> responseType, String organizationId) {
        String url = baseUrl + path;
        logger.debug("Making POST request to: {}", url);
        
        try {
            String jsonBody = objectMapper.writeValueAsString(body);
            
            HttpRequest request = addAuthHeaders(HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody)), organizationId)
                    .build();
            
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> handleResponse(response, responseType));
        } catch (Exception e) {
            CompletableFuture<R> future = new CompletableFuture<>();
            future.completeExceptionally(new RuntimeException("Error serializing request body: " + e.getMessage(), e));
            return future;
        }
    }

    /**
     * Make a DELETE request.
     */
    public <T> CompletableFuture<T> delete(String path, Class<T> responseType, String organizationId) {
        String url = baseUrl + path;
        logger.debug("Making DELETE request to: {}", url);
        
        HttpRequest request = addAuthHeaders(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .DELETE(), organizationId)
                .build();
        
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> handleResponse(response, responseType));
    }

    /**
     * Handle the HTTP response for a Class type.
     */
    private <T> T handleResponse(HttpResponse<String> response, Class<T> responseType) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            try {
                if (responseType == Void.class) {
                    return null;
                }
                
                if (response.body() == null || response.body().isEmpty()) {
                    return null;
                }
                
                return objectMapper.readValue(response.body(), responseType);
            } catch (Exception e) {
                throw new RuntimeException("Error deserializing response: " + e.getMessage(), e);
            }
        } else {
            throw new RuntimeException("API request failed with status code " + 
                response.statusCode() + ": " + response.body());
        }
    }

    /**
     * Handle the HTTP response for a TypeReference.
     */
    private <T> T handleResponse(HttpResponse<String> response, TypeReference<T> responseTypeRef) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            try {
                if (response.body() == null || response.body().isEmpty()) {
                    return null;
                }
                
                return objectMapper.readValue(response.body(), responseTypeRef);
            } catch (Exception e) {
                throw new RuntimeException("Error deserializing response with TypeReference: " + e.getMessage(), e);
            }
        } else {
            throw new RuntimeException("API request failed with status code " + 
                response.statusCode() + ": " + response.body());
        }
    }
}
