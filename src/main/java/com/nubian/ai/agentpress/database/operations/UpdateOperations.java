package com.nubian.ai.agentpress.database.operations;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.nubian.ai.agentpress.database.ResilientSupabaseClient;
import com.nubian.ai.agentpress.database.parsers.SqlParser;

/**
 * Handles generic database update operations.
 */
@Component
public class UpdateOperations {

    private static final Logger logger = LoggerFactory.getLogger(UpdateOperations.class);

    private final ResilientSupabaseClient supabaseClient;

    public UpdateOperations(ResilientSupabaseClient supabaseClient) {
        this.supabaseClient = supabaseClient;
    }

    /**
     * Update a record in the database.
     * 
     * @param tableName The table to update
     * @param values The values to set
     * @param conditions The conditions to filter by
     * @return A CompletableFuture that completes with the number of rows affected
     */
    public CompletableFuture<Integer> update(String tableName, Map<String, Object> values, Map<String, Object> conditions) {
        logger.info("DB UPDATE on table '{}' with data: {}", tableName, Map.of("values", values, "conditions", conditions));
        
        // Handle schema-qualified table names (schema.table format)
        String[] parts = tableName.split("\\.");
        if (parts.length > 1) {
            String schema = parts[0];
            String table = parts[1];
            logger.debug("Using schema('{}').update('{}') for schema-qualified table", schema, table);
            // Note: ResilientSupabaseClient.SchemaClient does not have a direct update method yet.
            // This would require adding it or using a more direct PostgREST builder approach.
            // For now, we'll fall back to the ResilientSupabaseClient's update which handles schema internally.
            return supabaseClient.update(tableName, values, conditions); 
        } else {
            // For specific known tables, enforce the 'basejump' schema
            if ("accounts".equals(tableName) || "account_user".equals(tableName)) {
                logger.warn("Applying 'basejump' schema by default for known table: {}. Consider using schema-qualified names or .schema() method.", tableName);
                // This will internally call ResilientSupabaseClient.update("basejump." + tableName, ...)
                return supabaseClient.update("basejump." + tableName, values, conditions);
            }
            // For other tables, use the default schema behavior of ResilientSupabaseClient
            return supabaseClient.update(tableName, values, conditions);
        }
    }

    /**
     * Update records with two parameters asynchronously - This is used by SandboxController.
     * 
     * @param sql The SQL update statement (for logging purposes only)
     * @param param1 The first parameter
     * @param param2 The second parameter
     * @return A CompletableFuture that completes with the number of rows affected
     */
    public CompletableFuture<Integer> updateAsync(String sql, String param1, String param2) {
        // Extract table name
        String tableName = SqlParser.extractTableName(sql);
        if (tableName == null) {
            logger.error("Could not extract table name from SQL: {}", sql);
            return CompletableFuture.completedFuture(0);
        }
        
        // Extract field to update and where field
        String setField = SqlParser.extractSetField(sql);
        String whereField = SqlParser.extractWhereField(sql);
        
        if (setField == null || whereField == null) {
            logger.error("Could not extract SET or WHERE fields from SQL: {}", sql);
            return CompletableFuture.completedFuture(0);
        }
        
        // Create values and conditions maps
        Map<String, Object> values = new HashMap<>();
        values.put(setField, param1);
        
        Map<String, Object> conditions = new HashMap<>();
        conditions.put(whereField, param2);
        
        // Perform the update using builder pattern
        return update(tableName, values, conditions);
    }
}
