package com.nubian.ai.postgrest;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * A client for Supabase PostgREST API.
 * This class provides methods for interacting with the database and storage.
 */
public class SupabaseClient {
    private static final Logger logger = LoggerFactory.getLogger(SupabaseClient.class);
    
    private final String supabaseUrl;
    private final String supabaseKey;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Map<String, String> defaultHeaders;
    private final String schema;

    /**
     * Create a new SupabaseClient
     *
     * @param supabaseUrl The Supabase URL
     * @param supabaseKey The Supabase API key
     */
    public SupabaseClient(String supabaseUrl, String supabaseKey) {
        this(supabaseUrl, supabaseKey, null);
    }
    
    /**
     * Create a new SupabaseClient with a specific schema
     *
     * @param supabaseUrl The Supabase URL
     * @param supabaseKey The Supabase API key
     * @param schema The database schema to use (defaults to "public" if null)
     */
    public SupabaseClient(String supabaseUrl, String supabaseKey, String schema) {
        this.supabaseUrl = supabaseUrl.endsWith("/") ? supabaseUrl.substring(0, supabaseUrl.length() - 1) : supabaseUrl;
        this.supabaseKey = supabaseKey;
        this.schema = schema; // Store schema as is; null means use PostgREST default (public)
        
        logger.debug("Initializing SupabaseClient with URL: {}, Explicit Schema: {}", this.supabaseUrl, this.schema == null ? "[None - Default to Public]" : this.schema);
        
        // Create HTTP client with reasonable timeouts
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        
        this.objectMapper = new ObjectMapper();
        
        // Set up default headers for all requests
        this.defaultHeaders = new HashMap<>();
        this.defaultHeaders.put("apikey", this.supabaseKey);
        this.defaultHeaders.put("Authorization", "Bearer " + this.supabaseKey);
        this.defaultHeaders.put("Content-Type", "application/json");
    }
    
    /**
     * Get a builder for the database API.
     * 
     * @return A DatabaseClient for interacting with the database
     */
    public DatabaseClient database() {
        return new DatabaseClient(this.supabaseUrl, this.supabaseKey, this.httpClient, this.objectMapper, this.defaultHeaders, this.schema);
    }
    
    /**
     * Select a schema to query.
     * The schema needs to be on the list of exposed schemas inside Supabase.
     * 
     * @param schema The schema name to use
     * @return A new SupabaseClient instance configured with the specified schema
     */
    public SupabaseClient schema(String schema) {
        if (schema == null || schema.isEmpty()) {
            throw new IllegalArgumentException("Schema name cannot be null or empty");
        }
        return new SupabaseClient(this.supabaseUrl, this.supabaseKey, schema);
    }
    
    /**
     * Get a builder for the storage API.
     * 
     * @return A StorageClient for interacting with storage
     */
    public StorageClient storage() {
        return new StorageClient(this.supabaseUrl, this.supabaseKey, this.httpClient, this.objectMapper, this.defaultHeaders, this.schema);
    }
    
    /**
     * Get a client for RPC operations.
     * 
     * @return An RpcClient for invoking RPC functions
     */
    public RpcClient rpc() {
        return new RpcClient(this.supabaseUrl, this.supabaseKey, this.httpClient, this.objectMapper, this.defaultHeaders, this.schema);
    }
    
    /**
     * Client for database operations.
     */
    public static class DatabaseClient {
        private final String supabaseUrl;
        private final String supabaseKey;
        private final OkHttpClient httpClient;
        private final ObjectMapper objectMapper;
        private final Map<String, String> defaultHeaders;
        private final String schema;
        
        /**
         * Create a new DatabaseClient
         */
        DatabaseClient(String supabaseUrl, String supabaseKey, OkHttpClient httpClient, 
                ObjectMapper objectMapper, Map<String, String> defaultHeaders, String schema) {
            this.supabaseUrl = supabaseUrl;
            this.supabaseKey = supabaseKey;
            this.httpClient = httpClient;
            this.objectMapper = objectMapper;
            this.defaultHeaders = defaultHeaders;
            this.schema = schema;
        }
        
        /**
         * Create a query builder for a table or view.
         *
         * @param table The name of the table or view
         * @return A PostgrestQueryBuilder for the table or view
         */
        public PostgrestQueryBuilder<Object, Object, String, Object> from(String table) {
            try {
                URL url = new URL(this.supabaseUrl + "/rest/v1/" + table);
                
                PostgrestQueryBuilder.PostgrestQueryBuilderOptions options = new PostgrestQueryBuilder.PostgrestQueryBuilderOptions();
                options.headers = this.defaultHeaders;
                options.schema = this.schema;
                
                return new PostgrestQueryBuilder<>(url, options);
            } catch (MalformedURLException e) {
                throw new RuntimeException("Invalid URL: " + e.getMessage(), e);
            }
        }
        
        /**
         * Alias for from() method. Used to match Python client's table() method.
         *
         * @param table The name of the table or view
         * @return A PostgrestQueryBuilder for the table or view
         */
        public PostgrestQueryBuilder<Object, Object, String, Object> table(String table) {
            return from(table);
        }
        
        /**
         * Insert a row into a table.
         *
         * @param table The name of the table
         * @param values The values to insert
         * @return A CompletableFuture with the response
         */
        public CompletableFuture<PostgrestResponse<Object>> insert(String table, Map<String, Object> values) {
            return from(table).insert(values).execute();
        }
        
        /**
         * Insert a row into a table with a specific return representation.
         *
         * @param table The name of the table
         * @param values The values to insert
         * @param returning What to return from the insert ("minimal", "representation", or "*")
         * @return A CompletableFuture with the response
         */
        public CompletableFuture<PostgrestResponse<Object>> insert(String table, Map<String, Object> values, String returning) {
            PostgrestQueryBuilder<Object, Object, String, Object> queryBuilder = from(table);
            
            // Set the Prefer header for the return type
            Map<String, Object> options = new HashMap<>();
            Map<String, String> headers = new HashMap<>(queryBuilder.headers);
            headers.put("Prefer", "return=" + returning);
            
            // Create builder options with the return preference
            PostgrestBuilder.PostgrestBuilderOptions builderOptions = new PostgrestBuilder.PostgrestBuilderOptions();
            builderOptions.url = queryBuilder.url;
            builderOptions.headers = headers;
            builderOptions.schema = queryBuilder.schema;
            builderOptions.method = "POST";
            builderOptions.body = values;
            
            // Create a filter builder with the custom options
            PostgrestFilterBuilder<Object, Map<String, Object>, Object, String, Object> filterBuilder = 
                new PostgrestFilterBuilder<>(builderOptions);
            
            return filterBuilder.execute();
        }
        
        /**
         * Insert multiple rows into a table.
         *
         * @param table The name of the table
         * @param values The values to insert
         * @return A CompletableFuture with the response
         */
        public CompletableFuture<PostgrestResponse<Object>> insert(String table, Map<String, Object>[] values) {
            return from(table).insert(values).execute();
        }
        
        /**
         * Insert multiple rows into a table with a specific return representation.
         *
         * @param table The name of the table
         * @param values The values to insert
         * @param returning What to return from the insert ("minimal", "representation", or "*")
         * @return A CompletableFuture with the response
         */
        public CompletableFuture<PostgrestResponse<Object>> insert(String table, Map<String, Object>[] values, String returning) {
            PostgrestQueryBuilder<Object, Object, String, Object> queryBuilder = from(table);
            
            // Set the Prefer header for the return type
            Map<String, Object> options = new HashMap<>();
            Map<String, String> headers = new HashMap<>(queryBuilder.headers);
            headers.put("Prefer", "return=" + returning);
            
            // Create builder options with the return preference
            PostgrestBuilder.PostgrestBuilderOptions builderOptions = new PostgrestBuilder.PostgrestBuilderOptions();
            builderOptions.url = queryBuilder.url;
            builderOptions.headers = headers;
            builderOptions.schema = queryBuilder.schema;
            builderOptions.method = "POST";
            builderOptions.body = values;
            
            // Create a filter builder with the custom options
            PostgrestFilterBuilder<Object, Map<String, Object>, Object, String, Object> filterBuilder = 
                new PostgrestFilterBuilder<>(builderOptions);
            
            return filterBuilder.execute();
        }
        
        /**
         * Update rows in a table.
         *
         * @param table The name of the table
         * @param values The values to update
         * @return A PostgrestFilterBuilder to apply filters to the update
         */
        public PostgrestFilterBuilder<Object, Map<String, Object>, Object, String, Object> update(
                String table, Map<String, Object> values) {
            return from(table).update(values);
        }
        
        /**
         * Delete rows from a table.
         *
         * @param table The name of the table
         * @return A PostgrestFilterBuilder to apply filters to the delete
         */
        public PostgrestFilterBuilder<Object, Map<String, Object>, Object, String, Object> delete(String table) {
            return from(table).delete();
        }
        
        /**
         * Select rows from a table.
         *
         * @param table The name of the table
         * @param columns The columns to select
         * @return A PostgrestFilterBuilder to apply filters to the select
         */
        public <ResultOne> PostgrestFilterBuilder<Object, Map<String, Object>, ResultOne[], String, Object> select(
                String table, String columns) {
            return from(table).select(columns);
        }
        
        /**
         * Select all columns from a table.
         *
         * @param table The name of the table
         * @return A PostgrestFilterBuilder to apply filters to the select
         */
        public <ResultOne> PostgrestFilterBuilder<Object, Map<String, Object>, ResultOne[], String, Object> select(
                String table) {
            return from(table).select("*");
        }
    }
    
    /**
     * Client for storage operations.
     */
    public static class StorageClient {
        private final String supabaseUrl;
        private final String supabaseKey;
        private final OkHttpClient httpClient;
        private final ObjectMapper objectMapper;
        private final Map<String, String> defaultHeaders;
        private final String schema;
        
        /**
         * Create a new StorageClient
         */
        StorageClient(String supabaseUrl, String supabaseKey, OkHttpClient httpClient, 
                ObjectMapper objectMapper, Map<String, String> defaultHeaders, String schema) {
            this.supabaseUrl = supabaseUrl;
            this.supabaseKey = supabaseKey;
            this.httpClient = httpClient;
            this.objectMapper = objectMapper;
            this.defaultHeaders = defaultHeaders;
            this.schema = schema;
        }
        
        /**
         * Upload a file to storage.
         *
         * @param path The path to upload to (bucket/filename)
         * @param fileContent The file content
         * @return A CompletableFuture with the response
         */
        public CompletableFuture<Map<String, Object>> upload(String path, byte[] fileContent) {
            // Split path into bucket and filename
            String[] parts = path.split("/", 2);
            if (parts.length != 2) {
                CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
                future.completeExceptionally(new IllegalArgumentException(
                        "Path should be in the format 'bucket/filename': " + path));
                return future;
            }
            
            String bucket = parts[0];
            String filename = parts[1];
            
            return uploadToStorage(bucket, filename, fileContent);
        }
        
        /**
         * Upload a file to a storage bucket.
         *
         * @param bucket The bucket name
         * @param filename The filename
         * @param fileContent The file content
         * @return A CompletableFuture with the response
         */
        private CompletableFuture<Map<String, Object>> uploadToStorage(
                String bucket, String filename, byte[] fileContent) {
            
            // This is a simplified implementation
            // In a real implementation, you would use the Supabase Storage API
            // For now, we just return a dummy response
            
            CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
            Map<String, Object> response = new HashMap<>();
            response.put("Key", bucket + "/" + filename);
            response.put("Size", fileContent.length);
            future.complete(response);
            
            return future;
        }
    }
    
    /**
     * Client for RPC operations.
     */
    public static class RpcClient {
        private final String supabaseUrl;
        private final String supabaseKey;
        private final OkHttpClient httpClient;
        private final ObjectMapper objectMapper;
        private final Map<String, String> defaultHeaders;
        private final String schema;
        
        /**
         * Create a new RpcClient
         */
        RpcClient(String supabaseUrl, String supabaseKey, OkHttpClient httpClient, 
                ObjectMapper objectMapper, Map<String, String> defaultHeaders, String schema) {
            this.supabaseUrl = supabaseUrl;
            this.supabaseKey = supabaseKey;
            this.httpClient = httpClient;
            this.objectMapper = objectMapper;
            this.defaultHeaders = defaultHeaders;
            this.schema = schema;
        }
        
        /**
         * Call an RPC function with parameters.
         *
         * @param functionName The name of the function to call
         * @param params The parameters to pass to the function
         * @return A PostgrestRpcBuilder for the RPC call
         */
        public <T> PostgrestRpcBuilder<T> function(String functionName, Map<String, Object> params) {
            try {
                URL url = new URL(this.supabaseUrl + "/rest/v1/rpc/" + functionName);
                
                PostgrestBuilder.PostgrestBuilderOptions options = new PostgrestBuilder.PostgrestBuilderOptions();
                options.url = url;
                options.headers = this.defaultHeaders;
                options.schema = this.schema;
                options.httpClient = this.httpClient;
                options.objectMapper = this.objectMapper;
                
                return new PostgrestRpcBuilder<>(functionName, params, options);
            } catch (Exception e) {
                throw new RuntimeException("Error creating RPC builder: " + e.getMessage(), e);
            }
        }
        
        /**
         * Call an RPC function without parameters.
         *
         * @param functionName The name of the function to call
         * @return A PostgrestRpcBuilder for the RPC call
         */
        public <T> PostgrestRpcBuilder<T> function(String functionName) {
            return function(functionName, new HashMap<>());
        }
    }
    
    /**
     * Helper class to build insert values.
     */
    public static class Insert {
        /**
         * Create a new row builder.
         *
         * @return A RowBuilder
         */
        public static RowBuilder row() {
            return new RowBuilder();
        }
        
        /**
         * Builder for a row.
         */
        public static class RowBuilder {
            private final Map<String, Object> values = new HashMap<>();
            
            /**
             * Add a column to the row.
             *
             * @param column The column name
             * @param value The column value
             * @return This RowBuilder
             */
            public RowBuilder column(String column, Object value) {
                this.values.put(column, value);
                return this;
            }
            
            /**
             * Get the built row.
             *
             * @return The row as a Map
             */
            public Map<String, Object> build() {
                return this.values;
            }
        }
    }
}
