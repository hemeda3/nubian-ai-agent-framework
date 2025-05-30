package com.nubian.ai.postgrest;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base builder class for constructing PostgREST requests.
 * Modeled after the Supabase PostgREST JavaScript client.
 * 
 * @param <T> The result type of the query
 */
public class PostgrestBuilder<T> {
    private static final Logger logger = LoggerFactory.getLogger(PostgrestBuilder.class);
    protected static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    
    protected URL url;
    protected Map<String, String> headers;
    protected String schema;
    protected String method;
    protected Object body;
    protected boolean allowEmpty;
    protected boolean isMaybeSingle;
    protected OkHttpClient httpClient;
    protected ObjectMapper objectMapper;
    protected AbortSignal signal;
    protected boolean shouldThrowOnError = false;

    /**
     * Create a new PostgrestBuilder
     */
    public PostgrestBuilder(PostgrestBuilderOptions options) {
        this.url = options.url;
        this.headers = options.headers != null ? options.headers : new HashMap<>();
        this.schema = options.schema;
        this.method = options.method;
        this.body = options.body;
        this.allowEmpty = options.allowEmpty;
        this.httpClient = options.httpClient != null ? options.httpClient : new OkHttpClient();
        this.objectMapper = options.objectMapper != null ? options.objectMapper : new ObjectMapper();
        this.signal = options.signal;
    }
    
    /**
     * If there's an error with the query, throwOnError will reject the promise by
     * throwing the error instead of returning it as part of a successful response.
     * 
     * @return This builder instance
     */
    public PostgrestBuilder<T> throwOnError() {
        this.shouldThrowOnError = true;
        return this;
    }

    /**
     * Execute the request.
     *
     * @return A CompletableFuture that completes with the parsed response data
     */
    public CompletableFuture<PostgrestResponse<T>> execute() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Apply schema headers only if schema is specified and not 'public'
                Map<String, String> finalHeaders = new HashMap<>(this.headers);
                if (this.schema != null && !this.schema.isEmpty() && !"public".equalsIgnoreCase(this.schema)) {
                    logger.debug("Applying schema profile headers for schema: {}", this.schema);
                    // For GET and HEAD requests, use Accept-Profile
                    // For other methods, use Content-Profile
                    if ("GET".equalsIgnoreCase(this.method) || "HEAD".equalsIgnoreCase(this.method)) {
                        finalHeaders.put("Accept-Profile", this.schema);
                    } else {
                        finalHeaders.put("Content-Profile", this.schema);
                    }
                } else {
                    logger.debug("No schema profile headers applied. Schema: {}", 
                        this.schema == null ? "[None/Default]" : this.schema);
                }
                
                Request.Builder requestBuilder = new Request.Builder()
                        .url(this.url.toString())
                        .method(this.method, this.prepareRequestBody());
                
                // Add headers
                Headers.Builder headersBuilder = new Headers.Builder();
                for (Map.Entry<String, String> entry : finalHeaders.entrySet()) {
                    headersBuilder.add(entry.getKey(), entry.getValue());
                }
                requestBuilder.headers(headersBuilder.build());
                
                Request request = requestBuilder.build();
                
                try (Response response = this.httpClient.newCall(request).execute()) {
                    PostgrestResponse<T> result = this.handleResponse(response);
                    
                    // If throwOnError is true and there's an error, throw it
                    if (this.shouldThrowOnError && result.getError() != null) {
                        throw result.getError();
                    }
                    
                    return result;
                }
            } catch (IOException e) {
                throw new RuntimeException("Error executing request: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Prepare the request body.
     * 
     * @return The request body, or null for methods that don't have a body
     */
    protected RequestBody prepareRequestBody() {
        if (this.method.equals("GET") || this.method.equals("HEAD")) {
            return null;
        }
        
        if (this.body == null) {
            return RequestBody.create("", null);
        }
        
        try {
            String json = this.objectMapper.writeValueAsString(this.body);
            return RequestBody.create(json, JSON);
        } catch (Exception e) {
            throw new RuntimeException("Error serializing request body: " + e.getMessage(), e);
        }
    }
    
    /**
     * Handle the HTTP response.
     * 
     * @param response The HTTP response
     * @return The parsed response data
     */
    protected PostgrestResponse<T> handleResponse(Response response) {
        try {
            int status = response.code();
            Map<String, String> responseHeaders = this.parseHeaders(response);
            String contentType = responseHeaders.getOrDefault("content-type", "");
            String responseBody = response.body() != null ? response.body().string() : "";
            
            if (status >= 400) {
                PostgrestError error = this.parseErrorResponse(responseBody, status);
                return PostgrestResponse.error(error, status, responseHeaders);
            }
            
            T data = this.parseSuccessResponse(responseBody, contentType);
            return PostgrestResponse.success(data, status, responseHeaders);
        } catch (IOException e) {
            throw new RuntimeException("Error handling response: " + e.getMessage(), e);
        }
    }
    
    /**
     * Parse the response headers.
     * 
     * @param response The HTTP response
     * @return A map of response headers
     */
    protected Map<String, String> parseHeaders(Response response) {
        Map<String, String> headers = new HashMap<>();
        for (String name : response.headers().names()) {
            headers.put(name.toLowerCase(), response.header(name));
        }
        return headers;
    }
    
    /**
     * Parse the error response.
     * 
     * @param body The response body
     * @param status The HTTP status code
     * @return The parsed error
     */
    protected PostgrestError parseErrorResponse(String body, int status) {
        try {
            Map<String, Object> error = this.objectMapper.readValue(body, Map.class);
            String message = (String) error.getOrDefault("message", "Unknown error");
            String details = (String) error.getOrDefault("details", "");
            String hint = (String) error.getOrDefault("hint", "");
            String code = (String) error.getOrDefault("code", "");
            
            return new PostgrestError(message, details, hint, code);
        } catch (Exception e) {
            return new PostgrestError("Error parsing response: " + body, "", "", "");
        }
    }
    
    /**
     * Parse the success response.
     * 
     * @param body The response body
     * @param contentType The response content type
     * @return The parsed data
     */
    @SuppressWarnings("unchecked")
    protected T parseSuccessResponse(String body, String contentType) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        
        try {
            if (contentType.contains("application/json")) {
                return (T) this.objectMapper.readValue(body, Object.class);
            } else if (contentType.contains("text/csv")) {
                return (T) body;
            } else if (contentType.contains("application/geo+json")) {
                return (T) this.objectMapper.readValue(body, Map.class);
            } else {
                return (T) body;
            }
        } catch (Exception e) {
            throw new RuntimeException("Error parsing response body: " + e.getMessage(), e);
        }
    }
    
    /**
     * Interface for an AbortSignal.
     */
    public interface AbortSignal {
        /**
         * Check if the request has been aborted.
         * 
         * @return true if the request has been aborted, false otherwise
         */
        boolean aborted();
        
        /**
         * Add a listener for abort events.
         * 
         * @param listener The listener to add
         */
        void addListener(Runnable listener);
    }
    
    /**
     * Options for creating a PostgrestBuilder.
     */
    public static class PostgrestBuilderOptions {
        public URL url;
        public Map<String, String> headers;
        public String schema;
        public String method;
        public Object body;
        public boolean allowEmpty;
        public OkHttpClient httpClient;
        public ObjectMapper objectMapper;
        public AbortSignal signal;
    }
}
