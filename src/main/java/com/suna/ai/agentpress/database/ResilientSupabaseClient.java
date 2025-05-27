package com.Nubian.ai.agentpress.database;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.Nubian.ai.postgrest.PostgrestQueryBuilder;
import com.Nubian.ai.postgrest.PostgrestResponse;
import com.Nubian.ai.postgrest.SupabaseClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Service
@Primary
public class ResilientSupabaseClient {
    private static final Logger logger = LoggerFactory.getLogger(ResilientSupabaseClient.class);
    
    private final ObjectMapper objectMapper;
    private final SupabaseClient rootClient; 

    @Autowired
    public ResilientSupabaseClient(
            @Value("${SUPABASE_URL}") String supabaseUrl,
            @Value("${SUPABASE_SERVICE_ROLE_KEY}") String supabaseKey,
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.rootClient = new SupabaseClient(supabaseUrl, supabaseKey);
        logger.info("Initialized ResilientSupabaseClient for URL: {}", supabaseUrl);
    }

    public SupabaseClient getInternalClient() {
        return this.rootClient;
    }

    @Deprecated
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<List<Map<String, Object>>> executeQuery(String sql, Object... params) {
        logger.warn("Deprecated: executeQuery with raw SQL. Use builder pattern methods.");
        return CompletableFuture.completedFuture(new ArrayList<>());
    }
    
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<List<Map<String, Object>>> queryForList(String table, Map<String, Object> conditions) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String tableName = table;
                String schemaName = null;
                String[] parts = table.split("\\.");
                if (parts.length > 1) {
                    schemaName = parts[0];
                    tableName = parts[1];
                }
                
                var query = (schemaName != null) ? 
                    rootClient.schema(schemaName).database().from(tableName) : 
                    rootClient.database().from(tableName);

                var selectQuery = query.select("*");

                if (conditions != null && !conditions.isEmpty()) {
                    selectQuery = selectQuery.match(conditions);
                }
                PostgrestResponse<Object[]> response = selectQuery.execute().get();
                
                if (!response.isSuccess()) {
                    logger.error("Query failed: {}", response.getError().getMessage());
                    return new ArrayList<>();
                }
                
                Object responseData = response.getData();
                List<Map<String, Object>> results = new ArrayList<>();
                if (responseData instanceof Object[] dataArray) {
                    for (Object item : dataArray) {
                        if (item instanceof Map) {
                            results.add((Map<String, Object>) item);
                        }
                    }
                } else if (responseData instanceof List dataList) {
                    for (Object item : dataList) {
                        if (item instanceof Map) {
                            results.add((Map<String, Object>) item);
                        }
                    }
                } else if (responseData instanceof Map) {
                    results.add((Map<String, Object>) responseData);
                }
                return results;
            } catch (Exception e) {
                logger.error("Error querying table {}: {}", table, e.getMessage(), e);
                return new ArrayList<>();
            }
        });
    }
    
    public SchemaClient schema(String schemaName) {
        return new SchemaClient(schemaName, this.rootClient, this.objectMapper);
    }
    
    public TableAccessBuilder table(String table) {
        return new TableAccessBuilder(table, this.rootClient, this.objectMapper);
    }
    
    public class SchemaClient {
        private final String schemaName;
        private final SupabaseClient schemaScopedClient; 
        
        public SchemaClient(String schemaName, SupabaseClient rootClient, ObjectMapper objectMapper) {
            this.schemaName = schemaName;
            this.schemaScopedClient = rootClient.schema(schemaName);
        }
        
        public TableAccessBuilder from(String tableName) {
            return new TableAccessBuilder(tableName, this.schemaScopedClient, ResilientSupabaseClient.this.objectMapper);
        }
        
        public CompletableFuture<List<Map<String, Object>>> queryForList(String tableName, Map<String, Object> conditions) {
            return ResilientSupabaseClient.this.queryForList(this.schemaName + "." + tableName, conditions);
        }
        
        public CompletableFuture<Map<String, Object>> insert(String tableName, Map<String, Object> data) {
            return ResilientSupabaseClient.this.insert(tableName, data, false, "representation", this.schemaScopedClient);
        }
        
        public CompletableFuture<Map<String, Object>> insert(String tableName, Map<String, Object> data, boolean upsert) {
            return ResilientSupabaseClient.this.insert(tableName, data, upsert, upsert ? "minimal" : "representation", this.schemaScopedClient);
        }

        public CompletableFuture<Map<String, Object>> insert(String tableName, Map<String, Object> data, boolean upsert, String returning) {
            return ResilientSupabaseClient.this.insert(tableName, data, upsert, returning, this.schemaScopedClient);
        }
    }

    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<Map<String, Object>> insert(String table, Map<String, Object> data) {
        return insert(table, data, false, "representation", this.rootClient);
    }
    
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<Map<String, Object>> insert(String table, Map<String, Object> data, boolean upsert) {
         return insert(table, data, upsert, upsert ? "minimal" : "representation", this.rootClient);
    }

    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<Map<String, Object>> insert(String table, Map<String, Object> data, String returning) {
        return insert(table, data, false, returning, this.rootClient);
    }

    private CompletableFuture<Map<String, Object>> insert(String tableOrTableName, Map<String, Object> data, boolean upsert, String returning, SupabaseClient baseClient) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("Attempting insert on '{}', upsert: {}, returning: {}", tableOrTableName, upsert, returning);
                
                String localTableName = tableOrTableName;
                SupabaseClient effectiveClient = baseClient; 
                String schemaForDirectInsert = null;

                if (baseClient == this.rootClient) { // If called with root client, tableOrTableName might be "schema.table"
                    String[] parts = tableOrTableName.split("\\.");
                    if (parts.length > 1) {
                        schemaForDirectInsert = parts[0];
                        localTableName = parts[1];
                        // For upsert, we need a schema-scoped client to use .from()
                        if (upsert) {
                           effectiveClient = this.rootClient.schema(schemaForDirectInsert);
                           logger.debug("Upsert: Using schema-scoped client for: {}.{}", schemaForDirectInsert, localTableName);
                        } else {
                            // For direct insert, effectiveClient remains rootClient, and full table name is constructed
                             logger.debug("Direct Insert: Using root client. Schema: {}, Table: {}", schemaForDirectInsert, localTableName);
                        }
                    }
                }
                // If baseClient is already schema-scoped (from SchemaClient.insert), tableOrTableName is just the table name.
                // And effectiveClient is already schemaScopedClient.

                CompletableFuture<PostgrestResponse<Object>> future;
                Map<String, Object> options = new HashMap<>();
                if (upsert) {
                    options.put("upsert", true);
                }

                if (upsert) {
                    var queryBuilder = effectiveClient.database().from(localTableName);
                    future = queryBuilder.insert(data, options).execute();
                } else {
                    // Use PostgrestClient's direct insert method which takes full table name if schema is involved
                    String fullTableNameForDirectCall;
                    if (effectiveClient != this.rootClient) { // Indicates it's a schemaScopedClient from SchemaClient
                        fullTableNameForDirectCall = localTableName; // Already just table name, client is scoped
                         future = effectiveClient.database().insert(fullTableNameForDirectCall, data, returning);
                    } else { // Root client, table name might be simple or schema.table
                         fullTableNameForDirectCall = tableOrTableName; // Use original as it might contain schema
                         future = effectiveClient.database().insert(fullTableNameForDirectCall, data, returning);
                    }
                    logger.debug("Using direct insert for non-upsert: Effective Table='{}'", fullTableNameForDirectCall);
                }
                
                PostgrestResponse<Object> response = future.get();
                
                if (!response.isSuccess()) {
                    String errorMessage = response.getError().getMessage();
                    logger.error("Insert failed for table '{}': {}", tableOrTableName, errorMessage);
                    throw new RuntimeException("Database error: " + errorMessage);
                }
                
                Object dataObject = response.getData();
                Map<String, Object> result = new HashMap<>();

                if (dataObject instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> dataList = (List<Map<String, Object>>) dataObject;
                    if (!dataList.isEmpty()) {
                        result = dataList.get(0); // Get the first element
                    }
                } else if (dataObject instanceof Object[] arr && arr.length > 0 && arr[0] instanceof Map) {
                    // Fallback if Jackson deserializes to Object[] instead of List
                    result = (Map<String, Object>) arr[0];
                } else if (dataObject instanceof Map) {
                    // This might be for "minimal" returning or if a single object is returned (not typical for "representation")
                    result = (Map<String, Object>) dataObject;
                }
                
                if (result.isEmpty() && dataObject != null && !(dataObject instanceof List && ((List<?>)dataObject).isEmpty()) ) {
                    // Log if we got some data but didn't parse it into the result map, and it wasn't an empty list
                    logger.warn("Insert for table '{}' returned data but was not parsed into result map. Data type: {}", tableOrTableName, dataObject.getClass().getName());
                }
                logger.debug("Successfully inserted record. Table: '{}', Result: {}", tableOrTableName, result);
                return result;
            } catch (Exception e) {
                logger.error("Error inserting into table '{}': {}", tableOrTableName, e.getMessage(), e);
                return new HashMap<>();
            }
        });
    }
    
    public class TableAccessBuilder {
        private final String tableName; 
        private final SupabaseClient client; 
        private final ObjectMapper objectMapper;
        
        public TableAccessBuilder(String tableName, SupabaseClient client, ObjectMapper objectMapper) {
            this.tableName = tableName; 
            this.client = client; 
            this.objectMapper = objectMapper;
        }
        
        public CompletableFuture<Map<String, Object>> insert(Map<String, Object> data) {
            return ResilientSupabaseClient.this.insert(this.tableName, data, false, "representation", this.client);
        }
        
        public CompletableFuture<Map<String, Object>> insert(Map<String, Object> data, String returning) {
            return ResilientSupabaseClient.this.insert(this.tableName, data, false, returning, this.client);
        }

        public CompletableFuture<Map<String, Object>> insert(Map<String, Object> data, boolean upsert, String returning) {
            return ResilientSupabaseClient.this.insert(this.tableName, data, upsert, returning, this.client);
        }
        
        public CompletableFuture<PostgrestResponse<Object[]>> select(String columns) {
            return client.database().from(tableName).select(columns).execute();
        }
        
        public CompletableFuture<PostgrestResponse<Object[]>> filter(String column, String operator, Object value) {
            return client.database().from(tableName).select("*").filter(column, operator, value).execute();
        }
        
        public CompletableFuture<PostgrestResponse<Object[]>> filterJson(String jsonPath, String operator, Object value) {
            return client.database().from(tableName).select("*").filterJson(jsonPath, operator, value).execute();
        }
    }
    
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<Integer> update(String table, Map<String, Object> data, Map<String, Object> conditions) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String tableName = table;
                String schema = null;
                String[] parts = table.split("\\.");
                if (parts.length > 1) {
                    schema = parts[0];
                    tableName = parts[1];
                }
                
                var target = (schema != null) ? rootClient.schema(schema).database().from(tableName) : rootClient.database().from(tableName);
                var queryBuilder = target.update(data);

                if (conditions != null && !conditions.isEmpty()) {
                    queryBuilder = queryBuilder.match(conditions);
                } else {
                     logger.warn("Updating all records in table {} without conditions!", table);
                }
                PostgrestResponse<Object> response = queryBuilder.execute().get();
                
                if (!response.isSuccess()) {
                    logger.error("Update failed: {}", response.getError().getMessage());
                    return 0;
                }
                
                Object dataObject = response.getData();
                int count = 0;
                if (dataObject instanceof Object[] arr) {
                    count = arr.length;
                } else if (dataObject instanceof Map) {
                    count = 1; 
                }
                logger.debug("Update affected {} rows in table {}", count, table);
                return count;
            } catch (Exception e) {
                logger.error("Error updating table {}: {}", table, e.getMessage(), e);
                return 0;
            }
        });
    }
    
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public CompletableFuture<Integer> delete(String table, Map<String, Object> conditions) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String tableName = table;
                String schema = null;
                String[] parts = table.split("\\.");
                if (parts.length > 1) {
                    schema = parts[0];
                    tableName = parts[1];
                }

                var target = (schema != null) ? rootClient.schema(schema).database().from(tableName) : rootClient.database().from(tableName);
                var queryBuilder = target.delete();

                if (conditions != null && !conditions.isEmpty()) {
                    queryBuilder = queryBuilder.match(conditions);
                } else {
                    logger.warn("Deleting all records in table {} without conditions!", table);
                }
                PostgrestResponse<Object> response = queryBuilder.execute().get();
                
                if (!response.isSuccess()) {
                    logger.error("Delete failed: {}", response.getError().getMessage());
                    return 0;
                }
                
                Object dataObject = response.getData();
                int count = 0;
                 if (dataObject instanceof Object[] arr) {
                    count = arr.length;
                } else if (dataObject instanceof Map) {
                    count = 1; 
                }
                logger.debug("Delete affected {} rows in table {}", count, table);
                return count;
            } catch (Exception e) {
                logger.error("Error deleting from table {}: {}", table, e.getMessage(), e);
                return 0;
            }
        });
    }
}
