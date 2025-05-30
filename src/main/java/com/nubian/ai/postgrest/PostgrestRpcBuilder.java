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

/**
 * Builder for executing RPC functions in Supabase.
 *
 * @param <T> The result type of the RPC call
 */
public class PostgrestRpcBuilder<T> extends PostgrestBuilder<T> {
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    
    private final String functionName;
    private final Map<String, Object> params;

    /**
     * Create a new PostgrestRpcBuilder
     * 
     * @param functionName The name of the RPC function to call
     * @param params The parameters to pass to the function
     * @param options Configuration options
     */
    public PostgrestRpcBuilder(String functionName, Map<String, Object> params, PostgrestBuilderOptions options) {
        super(options);
        this.functionName = functionName;
        this.params = params != null ? params : new HashMap<>();
        this.method = "POST";
        this.body = this.params;
    }

    /**
     * Execute the RPC call.
     *
     * @return A CompletableFuture that completes with the parsed response data
     */
    @Override
    public CompletableFuture<PostgrestResponse<T>> execute() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Apply schema headers if specified
                Map<String, String> headersWithSchema = new HashMap<>(this.headers);
                if (this.schema != null && !this.schema.isEmpty()) {
                    headersWithSchema.put("Content-Profile", this.schema);
                }
                
                // Create request body
                String json;
                try {
                    json = this.objectMapper.writeValueAsString(this.body);
                } catch (Exception e) {
                    throw new RuntimeException("Error serializing RPC parameters: " + e.getMessage(), e);
                }
                
                RequestBody requestBody = RequestBody.create(json, JSON);
                
                // Build request
                Request.Builder requestBuilder = new Request.Builder()
                        .url(this.url.toString())
                        .method(this.method, requestBody);
                
                // Add headers
                Headers.Builder headersBuilder = new Headers.Builder();
                for (Map.Entry<String, String> entry : headersWithSchema.entrySet()) {
                    headersBuilder.add(entry.getKey(), entry.getValue());
                }
                requestBuilder.headers(headersBuilder.build());
                
                Request request = requestBuilder.build();
                
                // Execute request
                try (Response response = this.httpClient.newCall(request).execute()) {
                    PostgrestResponse<T> result = this.handleResponse(response);
                    
                    // If throwOnError is true and there's an error, throw it
                    if (this.shouldThrowOnError && result.getError() != null) {
                        throw result.getError();
                    }
                    
                    return result;
                }
            } catch (IOException e) {
                throw new RuntimeException("Error executing RPC call: " + e.getMessage(), e);
            }
        });
    }
}
