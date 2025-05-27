package com.Nubian.ai.postgrest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

/**
 * Client for executing DDL (Data Definition Language) operations against Supabase.
 * This client enables schema management operations like creating and altering tables.
 */
public class SupabaseDdlClient {
    private static final Logger logger = LoggerFactory.getLogger(SupabaseDdlClient.class);
    
    private final String supabaseUrl;
    private final String supabaseKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String schema;
    
    /**
     * Constructor for the Supabase DDL client.
     * 
     * @param supabaseUrl The Supabase URL
     * @param supabaseKey The Supabase API key (service role key recommended for schema operations)
     */
    public SupabaseDdlClient(String supabaseUrl, String supabaseKey) {
        this(supabaseUrl, supabaseKey, null);
    }
    
    /**
     * Constructor for the Supabase DDL client with specific schema.
     * 
     * @param supabaseUrl The Supabase URL
     * @param supabaseKey The Supabase API key (service role key recommended for schema operations)
     * @param schema The database schema to use (defaults to "public" if null)
     */
    public SupabaseDdlClient(String supabaseUrl, String supabaseKey, String schema) {
        this.supabaseUrl = supabaseUrl;
        this.supabaseKey = supabaseKey;
        this.schema = schema;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        
        logger.debug("Initialized SupabaseDdlClient for URL: {}, Schema: {}", this.supabaseUrl, this.schema);
    }
    
    /**
     * Create a new SupabaseDdlClient with a specific schema.
     * 
     * @param schema The schema name to use
     * @return A new SupabaseDdlClient instance configured with the specified schema
     */
    public SupabaseDdlClient schema(String schema) {
        if (schema == null || schema.isEmpty()) {
            throw new IllegalArgumentException("Schema name cannot be null or empty");
        }
        return new SupabaseDdlClient(this.supabaseUrl, this.supabaseKey, schema);
    }
    
    /**
     * Execute a DDL SQL statement asynchronously.
     * 
     * IMPORTANT: This requires a custom SQL function to be installed in your Supabase database.
     * By default, Supabase does not allow direct execution of DDL statements through the REST API.
     * 
     * To use this functionality, you need to:
     * 1. Install a custom SQL function in your Supabase database (requires admin privileges)
     * 2. Use database migrations for schema changes instead of this client in production
     * 
     * @param sql The SQL DDL statement to execute
     * @return A CompletableFuture containing the result of the operation
     */
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<Map<String, Object>> executeDdl(String sql) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("Executing DDL SQL: {}", sql);
                
                // Log a warning about the limitations
                logger.warn("DDL operations through the REST API require a custom SQL function in Supabase");
                logger.warn("Consider using database migrations for schema changes instead");
                
                // Try both function names that might exist
                String[] possibleFunctions = {"pg_execute_sql", "execute_sql"};
                Exception lastException = null;
                
                for (String functionName : possibleFunctions) {
                    try {
                        // Prepare the request body
                        Map<String, String> requestBody = Map.of("query", sql);
                        String requestBodyJson = objectMapper.writeValueAsString(requestBody);
                        
                        // Prepare headers, including schema if specified
                        Map<String, String> headers = new HashMap<>();
                        headers.put("apikey", supabaseKey);
                        headers.put("Authorization", "Bearer " + supabaseKey);
                        headers.put("Content-Type", "application/json");
                        headers.put("Prefer", "return=representation");
                        
                        // Add schema header if specified
                        if (schema != null && !schema.isEmpty()) {
                            headers.put("Content-Profile", schema);
                        }
                        
                        // Create the HTTP request to the SQL execution RPC function
                        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                                .uri(URI.create(supabaseUrl + "/rest/v1/rpc/" + functionName))
                                .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson));
                        
                        // Add all headers
                        for (Map.Entry<String, String> entry : headers.entrySet()) {
                            requestBuilder.header(entry.getKey(), entry.getValue());
                        }
                        
                        HttpRequest request = requestBuilder.build();
                        
                        // Send the request
                        HttpResponse<String> response = httpClient.send(
                                request, 
                                HttpResponse.BodyHandlers.ofString());
                        
                        // Process the response
                        int statusCode = response.statusCode();
                        String responseBody = response.body();
                        
                        logger.debug("DDL SQL execution response status: {}", statusCode);
                        
                        // Parse the response
                        if (statusCode >= 200 && statusCode < 300) {
                            // Success
                            if (responseBody != null && !responseBody.isEmpty()) {
                                try {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> result = objectMapper.readValue(responseBody, Map.class);
                                    return result;
                                } catch (Exception e) {
                                    logger.warn("Could not parse response body as JSON: {}", e.getMessage());
                                    return Map.of(
                                        "success", true,
                                        "message", "Operation completed successfully",
                                        "rawResponse", responseBody
                                    );
                                }
                            } else {
                                return Map.of(
                                    "success", true,
                                    "message", "Operation completed successfully"
                                );
                            }
                        } else if (statusCode == 404 && responseBody.contains("Could not find the function")) {
                            // Function not found, will try the next one or return error at the end
                            lastException = new RuntimeException("Function " + functionName + " not found");
                            continue;
                        } else {
                            // Other error
                            logger.error("DDL SQL execution failed with status code {}: {}", statusCode, responseBody);
                            return Map.of(
                                "success", false,
                                "message", "Operation failed with status code " + statusCode,
                                "rawResponse", responseBody != null ? responseBody : ""
                            );
                        }
                    } catch (Exception e) {
                        lastException = e;
                        logger.error("Error executing DDL SQL with function {}: {}", functionName, e.getMessage());
                    }
                }
                
                // If we get here, all attempts failed
                String errorMessage = "DDL operations require a custom SQL function in your Supabase database. " +
                        "Neither pg_execute_sql nor execute_sql functions were found. " +
                        "Please check the migrations folder for SQL scripts to set up your database schema.";
                
                logger.error(errorMessage);
                
                if (lastException != null) {
                    logger.error("Last error: {}", lastException.getMessage());
                }
                
                return Map.of(
                    "success", false,
                    "message", errorMessage,
                    "suggestion", "Use database migrations instead of programmatic DDL"
                );
                
            } catch (Exception e) {
                logger.error("Error executing DDL SQL: {}", e.getMessage(), e);
                return Map.of(
                    "success", false,
                    "message", "Error executing DDL SQL: " + e.getMessage()
                );
            }
        });
    }
    
    /**
     * Create a new table in the Supabase database.
     * 
     * @param tableName The name of the table to create
     * @param columns Map of column names to their definitions (e.g., "id" -> "serial primary key")
     * @return A CompletableFuture containing the result of the operation
     */
    public CompletableFuture<Map<String, Object>> createTable(String tableName, Map<String, String> columns) {
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE IF NOT EXISTS ");
        
        // Add schema prefix if specified
        if (schema != null && !schema.isEmpty()) {
            sql.append(schema).append(".");
        }
        
        sql.append(tableName).append(" (");
        
        boolean first = true;
        for (Map.Entry<String, String> column : columns.entrySet()) {
            if (!first) {
                sql.append(", ");
            }
            sql.append(column.getKey()).append(" ").append(column.getValue());
            first = false;
        }
        
        sql.append(")");
        
        return executeDdl(sql.toString());
    }
    
    /**
     * Add a column to an existing table.
     * 
     * @param tableName The name of the table to alter
     * @param columnName The name of the column to add
     * @param columnDefinition The definition of the column (e.g., "text not null")
     * @return A CompletableFuture containing the result of the operation
     */
    public CompletableFuture<Map<String, Object>> addColumn(String tableName, String columnName, String columnDefinition) {
        String tableWithSchema = (schema != null && !schema.isEmpty()) ? 
                schema + "." + tableName : tableName;
                
        String sql = String.format("ALTER TABLE %s ADD COLUMN IF NOT EXISTS %s %s",
                tableWithSchema, columnName, columnDefinition);
        
        return executeDdl(sql);
    }
    
    /**
     * Create an index on a table.
     * 
     * @param tableName The name of the table to create an index on
     * @param indexName The name of the index to create
     * @param columns The columns to include in the index
     * @param unique Whether the index should enforce uniqueness
     * @return A CompletableFuture containing the result of the operation
     */
    public CompletableFuture<Map<String, Object>> createIndex(String tableName, String indexName, 
            String[] columns, boolean unique) {
        
        String tableWithSchema = (schema != null && !schema.isEmpty()) ? 
                schema + "." + tableName : tableName;
        
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE ");
        
        if (unique) {
            sql.append("UNIQUE ");
        }
        
        sql.append("INDEX IF NOT EXISTS ").append(indexName)
            .append(" ON ").append(tableWithSchema).append(" (");
        
        for (int i = 0; i < columns.length; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(columns[i]);
        }
        
        sql.append(")");
        
        return executeDdl(sql.toString());
    }
    
    /**
     * Drop a table from the database.
     * 
     * @param tableName The name of the table to drop
     * @param ifExists Whether to include IF EXISTS in the SQL statement
     * @param cascade Whether to include CASCADE in the SQL statement
     * @return A CompletableFuture containing the result of the operation
     */
    public CompletableFuture<Map<String, Object>> dropTable(String tableName, boolean ifExists, boolean cascade) {
        StringBuilder sql = new StringBuilder();
        sql.append("DROP TABLE ");
        
        if (ifExists) {
            sql.append("IF EXISTS ");
        }
        
        // Add schema prefix if specified
        if (schema != null && !schema.isEmpty()) {
            sql.append(schema).append(".");
        }
        
        sql.append(tableName);
        
        if (cascade) {
            sql.append(" CASCADE");
        }
        
        return executeDdl(sql.toString());
    }
    
    /**
     * Execute a batch of DDL statements as a single transaction.
     * 
     * @param sqlStatements The SQL statements to execute
     * @return A CompletableFuture containing the result of the operation
     */
    public CompletableFuture<Map<String, Object>> executeBatch(String[] sqlStatements) {
        StringBuilder sql = new StringBuilder();
        sql.append("BEGIN;\n");
        
        for (String statement : sqlStatements) {
            sql.append(statement).append(";\n");
        }
        
        sql.append("COMMIT;");
        
        return executeDdl(sql.toString());
    }
    
    /**
     * Apply a database migration from a SQL file.
     * 
     * @param migrationSql The full SQL content of the migration
     * @return A CompletableFuture containing the result of the operation
     */
    public CompletableFuture<Map<String, Object>> applyMigration(String migrationSql) {
        return executeDdl(migrationSql);
    }
    
    /**
     * Create a prepared statement for schema management operations.
     * This allows for builder-style creation of schema definitions.
     * 
     * @return A new SchemaBuilder instance
     */
    public SchemaBuilder schema() {
        return new SchemaBuilder(this);
    }
    
    /**
     * Helper class for building schema definitions with a fluent API.
     */
    public static class SchemaBuilder {
        private final SupabaseDdlClient client;
        private final Map<String, String> columns = new HashMap<>();
        private String tableName;
        
        private SchemaBuilder(SupabaseDdlClient client) {
            this.client = client;
        }
        
        /**
         * Set the name of the table to create.
         * 
         * @param tableName The name of the table
         * @return This SchemaBuilder instance for chaining
         */
        public SchemaBuilder createTable(String tableName) {
            this.tableName = tableName;
            return this;
        }
        
        /**
         * Add a column to the table definition.
         * 
         * @param name The name of the column
         * @param definition The definition of the column (e.g., "text not null")
         * @return This SchemaBuilder instance for chaining
         */
        public SchemaBuilder column(String name, String definition) {
            columns.put(name, definition);
            return this;
        }
        
        /**
         * Add an ID column with serial primary key.
         * 
         * @return This SchemaBuilder instance for chaining
         */
        public SchemaBuilder idColumn() {
            columns.put("id", "serial primary key");
            return this;
        }
        
        /**
         * Add a UUID column as primary key with default random UUID.
         * 
         * @param columnName The name of the UUID column (default: "id")
         * @return This SchemaBuilder instance for chaining
         */
        public SchemaBuilder uuidColumn(String columnName) {
            columns.put(columnName, "uuid primary key default gen_random_uuid()");
            return this;
        }
        
        /**
         * Add a UUID column as primary key with default random UUID.
         * 
         * @return This SchemaBuilder instance for chaining
         */
        public SchemaBuilder uuidColumn() {
            return uuidColumn("id");
        }
        
        /**
         * Add a timestamp column with default current timestamp.
         * 
         * @param columnName The name of the timestamp column
         * @return This SchemaBuilder instance for chaining
         */
        public SchemaBuilder timestampColumn(String columnName) {
            columns.put(columnName, "timestamp with time zone default now()");
            return this;
        }
        
        /**
         * Add standard created_at and updated_at timestamp columns.
         * 
         * @return This SchemaBuilder instance for chaining
         */
        public SchemaBuilder timestamps() {
            columns.put("created_at", "timestamp with time zone default now()");
            columns.put("updated_at", "timestamp with time zone default now()");
            return this;
        }
        
        /**
         * Execute the schema creation.
         * 
         * @return A CompletableFuture containing the result of the operation
         * @throws IllegalStateException if table name is not set
         */
        public CompletableFuture<Map<String, Object>> execute() {
            if (tableName == null || tableName.isEmpty()) {
                throw new IllegalStateException("Table name is required");
            }
            
            if (columns.isEmpty()) {
                throw new IllegalStateException("At least one column is required");
            }
            
            return client.createTable(tableName, columns);
        }
    }
}
