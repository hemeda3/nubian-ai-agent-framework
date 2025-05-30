package com.nubian.ai.agentpress.database.operations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nubian.ai.agentpress.database.ResilientSupabaseClient;
import com.nubian.ai.agentpress.database.parsers.SqlParser;
import com.nubian.ai.agentpress.database.utils.TypeConverter;

/**
 * Handles generic database query operations.
 */
@Component
public class QueryOperations {

    private static final Logger logger = LoggerFactory.getLogger(QueryOperations.class);

    private final ResilientSupabaseClient supabaseClient;
    private final ObjectMapper objectMapper;
    private final TypeConverter typeConverter;

    public QueryOperations(ResilientSupabaseClient supabaseClient, ObjectMapper objectMapper) {
        this.supabaseClient = supabaseClient;
        this.objectMapper = objectMapper;
        this.typeConverter = new TypeConverter(objectMapper);
    }

    /**
     * Query for a list of records using table and condition mapping.
     * 
     * @param tableName The table to query
     * @param conditions The conditions to filter by
     * @return A CompletableFuture that completes with the list of records
     */
    public CompletableFuture<List<Map<String, Object>>> queryForList(String tableName, Map<String, Object> conditions) {
        logger.info("DB QUERY on table '{}' with conditions: {}", tableName, conditions);
        
        // Handle schema-qualified table names (schema.table format)
        String[] parts = tableName.split("\\.");
        if (parts.length > 1) {
            String schema = parts[0];
            String table = parts[1];
            logger.debug("Using schema('{}').queryForList('{}') for schema-qualified table", schema, table);
            return supabaseClient.schema(schema).queryForList(table, conditions);
        } else {
            // For specific known tables, enforce the 'basejump' schema
            if ("accounts".equals(tableName) || "account_user".equals(tableName)) {
                logger.warn("Applying 'basejump' schema by default for known table: {}. Consider using schema-qualified names or .schema() method.", tableName);
                return supabaseClient.schema("basejump").queryForList(tableName, conditions);
            }
            // For other tables, use the default schema behavior of ResilientSupabaseClient
            return supabaseClient.queryForList(tableName, conditions);
        }
    }
    
    /**
     * Query for a list of records with a single parameter.
     * 
     * @param tableName The table to query
     * @param fieldName The field to filter on
     * @param value The value to match
     * @return A CompletableFuture that completes with the list of records
     */
    public CompletableFuture<List<Map<String, Object>>> queryForList(String tableName, String fieldName, Object value) {
        Map<String, Object> conditions = new HashMap<>();
        conditions.put(fieldName, value);
        return queryForList(tableName, conditions);
    }
    
    /**
     * Query for a list of records with two parameters.
     * 
     * @param tableName The table to query
     * @param field1 The first field to filter on
     * @param value1 The first value to match
     * @param field2 The second field to filter on
     * @param value2 The second value to match
     * @return A CompletableFuture that completes with the list of records
     */
    public CompletableFuture<List<Map<String, Object>>> queryForList(
            String tableName, String field1, Object value1, String field2, Object value2) {
        Map<String, Object> conditions = new HashMap<>();
        conditions.put(field1, value1);
        conditions.put(field2, value2);
        return queryForList(tableName, conditions);
    }

    /**
     * Query for a single object from the database.
     * 
     * @param <T> The type of object to return
     * @param sql The SQL query
     * @param resultClass The class of the result
     * @param param The parameter value
     * @return A CompletableFuture that completes with the single object result
     */
    public <T> CompletableFuture<T> queryForObject(String sql, Class<T> resultClass, String param) {
        logger.debug("Executing queryForObject with SQL: {} and parameter: {}", sql, param);
        
        // Extract table name from SQL
        String tableName = SqlParser.extractTableName(sql);
        if (tableName == null) {
            logger.error("Could not extract table name from SQL: {}", sql);
            return CompletableFuture.completedFuture(null);
        }
        
        // Extract field name from WHERE clause
        String fieldName = SqlParser.extractWhereField(sql);
        if (fieldName == null) {
            logger.error("Could not extract field name from SQL: {}", sql);
            return CompletableFuture.completedFuture(null);
        }
        
        // Create conditions map
        Map<String, Object> conditions = new HashMap<>();
        conditions.put(fieldName, param);
        
        // Execute the query
        return queryForList(tableName, conditions)
            .thenApply(results -> {
                if (results != null && !results.isEmpty()) {
                    Map<String, Object> result = results.get(0);
                    return typeConverter.convertResult(result, resultClass);
                }
                return null;
            })
            .exceptionally(e -> {
                logger.error("Failed to execute queryForObject: {}", e.getMessage(), e);
                return null;
            });
    }
}
