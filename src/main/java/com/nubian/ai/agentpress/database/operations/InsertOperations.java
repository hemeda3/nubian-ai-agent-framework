package com.nubian.ai.agentpress.database.operations;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.nubian.ai.agentpress.database.ResilientSupabaseClient;

/**
 * Handles generic database insert operations.
 */
@Component
public class InsertOperations {

    private static final Logger logger = LoggerFactory.getLogger(InsertOperations.class);

    private final ResilientSupabaseClient supabaseClient;

    public InsertOperations(ResilientSupabaseClient supabaseClient) {
        this.supabaseClient = supabaseClient;
    }

    /**
     * Insert a record into the database.
     * 
     * @param tableName The table to insert into
     * @param data The data to insert
     * @param upsert Whether to upsert (update if exists, insert if not)
     * @return A CompletableFuture that completes with the inserted record
     */
    public CompletableFuture<Map<String, Object>> insert(String tableName, Map<String, Object> data, boolean upsert) {
        logger.info("DB INSERT on table '{}' with data: {}", tableName, Map.of("data", data, "upsert", upsert));
        
        // Handle schema-qualified table names (schema.table format)
        String[] parts = tableName.split("\\.");
        if (parts.length > 1) {
            String schema = parts[0];
            String table = parts[1];
            logger.debug("Using schema('{}').insert('{}') for schema-qualified table, returning 'representation'", schema, table);
            return supabaseClient.schema(schema).insert(table, data, upsert, "representation");
        } else {
            // For specific known tables, enforce the 'basejump' schema
            if ("accounts".equals(tableName) || "account_user".equals(tableName)) {
                logger.warn("Applying 'basejump' schema by default for known table: {}. Returning 'representation'. Consider using schema-qualified names or .schema() method.", tableName);
                return supabaseClient.schema("basejump").insert(tableName, data, upsert, "representation");
            }
            // For other tables, use the default schema behavior of ResilientSupabaseClient
            if (upsert) {
                // When upsert is true, ResilientSupabaseClient.insert(table, data, upsert) implies "minimal" returning.
                // If "representation" is strictly needed for upsert=true here, ResilientSupabaseClient needs adjustment.
                // For now, adhering to available public methods.
                logger.debug("Using ResilientSupabaseClient.insert(tableName, data, true) for upsert, returning 'minimal'");
                return supabaseClient.insert(tableName, data, true);
            } else {
                logger.debug("Using ResilientSupabaseClient.insert(tableName, data, \"representation\") for non-upsert");
                return supabaseClient.insert(tableName, data, "representation");
            }
        }
    }
}
