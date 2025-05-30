package com.nubian.ai.agentpress.database.legacy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.nubian.ai.agentpress.database.operations.InsertOperations;
import com.nubian.ai.agentpress.database.operations.QueryOperations;
import com.nubian.ai.agentpress.database.operations.UpdateOperations;
import com.nubian.ai.agentpress.database.parsers.SqlParser;

/**
 * Provides compatibility methods for legacy SQL operations that parse SQL strings.
 * These methods should be phased out over time in favor of direct API calls.
 */
@Component
public class LegacySqlOperations {

    private static final Logger logger = LoggerFactory.getLogger(LegacySqlOperations.class);

    private final InsertOperations insertOperations;
    private final UpdateOperations updateOperations;
    private final QueryOperations queryOperations;

    public LegacySqlOperations(InsertOperations insertOperations, UpdateOperations updateOperations, QueryOperations queryOperations) {
        this.insertOperations = insertOperations;
        this.updateOperations = updateOperations;
        this.queryOperations = queryOperations;
    }

    /**
     * Update records with SQL - supports standard parameter replacement.
     * 
     * @param sql The SQL update statement
     * @param params The parameters
     * @return The number of rows affected
     */
    public int update(String sql, Object... params) {
        logger.debug("Executing legacy update with SQL: {} and {} parameters", sql, params.length);
        
        if (sql.toUpperCase().startsWith("INSERT INTO ")) {
            return handleInsert(sql, params);
        } else if (sql.toUpperCase().startsWith("UPDATE ")) {
            return handleUpdate(sql, params);
        } else {
            logger.error("Unsupported SQL operation for legacy update: {}", sql);
            return 0;
        }
    }
    
    /**
     * Handle INSERT statements.
     */
    private int handleInsert(String sql, Object[] params) {
        try {
            // Extract table name
            String tableName = SqlParser.extractTableName(sql);
            if (tableName == null) {
                logger.error("Could not extract table name from SQL: {}", sql);
                return 0;
            }
            
            // Extract column names from SQL between the first set of parentheses
            int openParen = sql.indexOf('(');
            int closeParen = sql.indexOf(')');
            if (openParen == -1 || closeParen == -1 || openParen > closeParen) {
                logger.error("Invalid INSERT SQL format: {}", sql);
                return 0;
            }
            String columnsPart = sql.substring(openParen + 1, closeParen);
            String[] columns = columnsPart.split(",");
            for (int i = 0; i < columns.length; i++) {
                columns[i] = columns[i].trim();
            }
            
            // Create data map from columns and parameters
            Map<String, Object> data = new HashMap<>();
            for (int i = 0; i < columns.length && i < params.length; i++) {
                // Fix for the 'created_by' column issue - map to the correct column name
                String columnName = columns[i];
                if ("created_by".equals(columnName)) {
                    columnName = "user_id"; // Assuming this is the correct column in the schema
                }
                data.put(columnName, params[i]);
            }
            
            // Insert the data
            Map<String, Object> result = insertOperations.insert(tableName, data, false).join();
            return result.isEmpty() ? 0 : 1;
        } catch (Exception e) {
            logger.error("Error during legacy insert: {}", e.getMessage(), e);
            return 0;
        }
    }
    
    /**
     * Handle UPDATE statements.
     */
    private int handleUpdate(String sql, Object[] params) {
        try {
            // Extract table name
            String tableName = SqlParser.extractTableName(sql);
            if (tableName == null) {
                logger.error("Could not extract table name from SQL: {}", sql);
                return 0;
            }
            
            // Extract SET clause values
            int setStart = sql.toUpperCase().indexOf(" SET ") + 5;
            int whereStart = sql.toUpperCase().indexOf(" WHERE ");
            if (setStart == -1 || whereStart == -1 || setStart > whereStart) {
                logger.error("Invalid UPDATE SQL format: {}", sql);
                return 0;
            }
            String setClause = sql.substring(setStart, whereStart);
            String[] setPairs = setClause.split(",");
            
            // Create values map
            Map<String, Object> values = new HashMap<>();
            int paramIndex = 0;
            for (String pair : setPairs) {
                if (paramIndex >= params.length) break;
                
                String[] parts = pair.split("=");
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    
                    // Fix for the 'created_by' column issue
                    if ("created_by".equals(key)) {
                        key = "user_id"; // Assuming this is the correct column in the schema
                    }
                    
                    values.put(key, params[paramIndex++]);
                }
            }
            
            // Extract WHERE clause
            String whereClause = sql.substring(whereStart + 7);
            String[] wherePairs = whereClause.split(" AND ");
            
            // Create conditions map
            Map<String, Object> conditions = new HashMap<>();
            for (String pair : wherePairs) {
                if (paramIndex >= params.length) break;
                
                String[] parts = pair.split("=");
                if (parts.length == 2) {
                    String key = parts[0].trim();
                    conditions.put(key, params[paramIndex++]);
                }
            }
            
            // Perform the update
            return updateOperations.update(tableName, values, conditions).join();
        } catch (Exception e) {
            logger.error("Error during legacy update: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Query for a list of records with a single parameter.
     * 
     * @param sql The SQL query (for logging purposes only)
     * @param param The parameter
     * @return A CompletableFuture that completes with a list of records
     */
    public CompletableFuture<List<Map<String, Object>>> queryForList(String sql, String param) {
        // Extract first table name from SQL by looking for FROM
        String tableName = SqlParser.extractTableName(sql);
        if (tableName == null) {
            logger.error("Could not extract table name from SQL: {}", sql);
            return CompletableFuture.completedFuture(List.of());
        }
        
        // Get first field name from WHERE clause
        String fieldName = SqlParser.extractWhereField(sql);
        if (fieldName == null) {
            logger.error("Could not extract field name from SQL: {}", sql);
            return CompletableFuture.completedFuture(List.of());
        }
        
        return queryOperations.queryForList(tableName, fieldName, param);
    }
    
    /**
     * Query for a list of records with two parameters.
     * 
     * @param sql The SQL query (for logging purposes only)
     * @param param1 The first parameter
     * @param param2 The second parameter
     * @return A CompletableFuture that completes with a list of records
     */
    public CompletableFuture<List<Map<String, Object>>> queryForList(String sql, String param1, String param2) {
        // Extract first table name from SQL by looking for FROM
        String tableName = SqlParser.extractTableName(sql);
        if (tableName == null) {
            logger.error("Could not extract table name from SQL: {}", sql);
            return CompletableFuture.completedFuture(List.of());
        }
        
        // Extract field names
        String[] fields = SqlParser.extractTwoWhereFields(sql);
        if (fields == null || fields.length < 2 || fields[0] == null) {
            logger.error("Could not extract field names from SQL: {}", sql);
            return CompletableFuture.completedFuture(List.of());
        }
        
        if (fields[1] != null) {
            return queryOperations.queryForList(tableName, fields[0], param1, fields[1], param2);
        } else {
            return queryOperations.queryForList(tableName, fields[0], param1);
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
        return updateOperations.update(tableName, values, conditions);
    }
}
