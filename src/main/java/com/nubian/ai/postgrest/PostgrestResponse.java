package com.nubian.ai.postgrest;

import java.util.Map;

/**
 * Response wrapper for PostgrestBuilder operations.
 * 
 * @param <T> The type of data returned by the query
 */
public class PostgrestResponse<T> {
    private final T data;
    private final PostgrestError error;
    private final int status;
    private final Map<String, String> headers;
    private final int count;
    
    private PostgrestResponse(T data, PostgrestError error, int status, Map<String, String> headers) {
        this.data = data;
        this.error = error;
        this.status = status;
        this.headers = headers;
        
        // Parse count from content-range header if available
        String contentRange = headers.get("content-range");
        if (contentRange != null && contentRange.contains("/")) {
            String countStr = contentRange.split("/")[1];
            this.count = countStr.equals("*") ? 0 : Integer.parseInt(countStr);
        } else {
            this.count = 0;
        }
    }
    
    /**
     * Create a successful response.
     * 
     * @param data The response data
     * @param status The HTTP status code
     * @param headers The response headers
     * @param <T> The type of data returned
     * @return A new PostgrestResponse instance
     */
    public static <T> PostgrestResponse<T> success(T data, int status, Map<String, String> headers) {
        return new PostgrestResponse<>(data, null, status, headers);
    }
    
    /**
     * Create an error response.
     * 
     * @param error The error that occurred
     * @param status The HTTP status code
     * @param headers The response headers
     * @param <T> The type of data returned
     * @return A new PostgrestResponse instance
     */
    public static <T> PostgrestResponse<T> error(PostgrestError error, int status, Map<String, String> headers) {
        return new PostgrestResponse<>(null, error, status, headers);
    }
    
    /**
     * Get the response data.
     * 
     * @return The response data
     */
    public T getData() {
        return data;
    }
    
    /**
     * Get the error that occurred, if any.
     * 
     * @return The error, or null if no error occurred
     */
    public PostgrestError getError() {
        return error;
    }
    
    /**
     * Get the HTTP status code.
     * 
     * @return The status code
     */
    public int getStatus() {
        return status;
    }
    
    /**
     * Get the response headers.
     * 
     * @return The headers
     */
    public Map<String, String> getHeaders() {
        return headers;
    }
    
    /**
     * Get the count of records from the content-range header.
     * 
     * @return The count
     */
    public int getCount() {
        return count;
    }
    
    /**
     * Check if the response was successful.
     * 
     * @return true if successful, false otherwise
     */
    public boolean isSuccess() {
        return error == null;
    }
}
