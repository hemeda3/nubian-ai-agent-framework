package com.nubian.ai.agentpress.database.operations;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.nubian.ai.agentpress.database.ResilientSupabaseClient;

/**
 * Handles generic database delete operations.
 */
@Component
public class DeleteOperations {

    private static final Logger logger = LoggerFactory.getLogger(DeleteOperations.class);

    private final ResilientSupabaseClient supabaseClient;

    public DeleteOperations(ResilientSupabaseClient supabaseClient) {
        this.supabaseClient = supabaseClient;
    }

    /**
     * Delete records from the database.
     * 
     * @param tableName The table to delete from
     * @param conditions The conditions to filter by
     * @return A CompletableFuture that completes with the number of rows affected
     */
    public CompletableFuture<Integer> delete(String tableName, Map<String, Object> conditions) {
        logger.info("DB DELETE on table '{}' with conditions: {}", tableName, conditions);

        // Handle schema-qualified table names (schema.table format)
        String[] parts = tableName.split("\\.");
        if (parts.length > 1) {
            String schema = parts[0];
            String table = parts[1];
            logger.debug("Using schema('{}').delete('{}') for schema-qualified table", schema, table);
            // Note: ResilientSupabaseClient.SchemaClient does not have a direct delete method yet.
            // This would require adding it or using a more direct PostgREST builder approach.
            // For now, we'll fall back to the ResilientSupabaseClient's delete which handles schema internally.
            return supabaseClient.delete(tableName, conditions);
        } else {
            // For specific known tables, enforce the 'basejump' schema
            if ("accounts".equals(tableName) || "account_user".equals(tableName)) {
                logger.warn("Applying 'basejump' schema by default for known table: {}. Consider using schema-qualified names or .schema() method.", tableName);
                return supabaseClient.delete("basejump." + tableName, conditions);
            }
            // For other tables, use the default schema behavior of ResilientSupabaseClient
            return supabaseClient.delete(tableName, conditions);
        }
    }
}
