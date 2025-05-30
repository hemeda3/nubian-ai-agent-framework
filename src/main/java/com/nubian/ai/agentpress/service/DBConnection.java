package com.nubian.ai.agentpress.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.nubian.ai.agentpress.database.core.DBConnectionFacade;
import com.nubian.ai.agentpress.model.Message;

/**
 * Database connection service for AgentPress.
 * 
 * Provides methods for interacting with the database, including
 * message storage and retrieval. This class now acts as a wrapper
 * around DBConnectionFacade for backward compatibility.
 */
@Service
public class DBConnection {
    private static final Logger logger = LoggerFactory.getLogger(DBConnection.class);
    
    private final DBConnectionFacade dbConnectionFacade;
    
    /**
     * Create a new database connection.
     * 
     * @param dbConnectionFacade The facade for database operations
     */
    @Autowired
    public DBConnection(DBConnectionFacade dbConnectionFacade) {
        this.dbConnectionFacade = dbConnectionFacade;
        logger.debug("Initialized DBConnection with DBConnectionFacade");
    }
    
    /**
     * Insert a message into the database.
     * 
     * @param message The message to insert
     * @return A CompletableFuture that completes with the inserted message
     */
    public CompletableFuture<Message> insertMessage(Message message) {
        return dbConnectionFacade.insertMessage(message);
    }
    
    /**
     * Get all messages for a thread.
     * 
     * @param threadId The ID of the thread
     * @return A CompletableFuture that completes with the list of messages
     */
    public CompletableFuture<List<Map<String, Object>>> getMessages(String threadId) {
        return dbConnectionFacade.getMessages(threadId);
    }
    
    /**
     * Get LLM-formatted messages for a thread.
     * 
     * @param threadId The ID of the thread
     * @return A CompletableFuture that completes with the list of LLM-formatted messages
     */
    public CompletableFuture<List<Map<String, Object>>> getLlmFormattedMessages(String threadId) {
        return dbConnectionFacade.getLlmFormattedMessages(threadId);
    }
    
    /**
     * Query for a list of records using table and condition mapping.
     * 
     * @param tableName The table to query
     * @param conditions The conditions to filter by
     * @return A CompletableFuture that completes with the list of records
     */
    public CompletableFuture<List<Map<String, Object>>> queryForList(String tableName, Map<String, Object> conditions) {
        return dbConnectionFacade.queryForList(tableName, conditions);
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
        return dbConnectionFacade.queryForList(tableName, fieldName, value);
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
        return dbConnectionFacade.queryForList(tableName, field1, value1, field2, value2);
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
        return dbConnectionFacade.queryForObject(sql, resultClass, param);
    }
    
    /**
     * Delete messages of a specific type for a given thread.
     *
     * @param threadId The ID of the thread
     * @param messageType The type of messages to delete
     * @return A CompletableFuture that completes with the number of deleted messages
     */
    public CompletableFuture<Integer> deleteMessagesByType(String threadId, String messageType) {
        return dbConnectionFacade.deleteMessagesByType(threadId, messageType);
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
        return dbConnectionFacade.update(tableName, values, conditions);
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
        return dbConnectionFacade.insert(tableName, data, upsert);
    }
    
    /**
     * Delete records from the database.
     * 
     * @param tableName The table to delete from
     * @param conditions The conditions to filter by
     * @return A CompletableFuture that completes with the number of rows affected
     */
    public CompletableFuture<Integer> delete(String tableName, Map<String, Object> conditions) {
        return dbConnectionFacade.delete(tableName, conditions);
    }
    
    /**
     * Compatibility methods for legacy code
     * These should be phased out over time
     */
    
    /**
     * Query for a list of records with a single parameter.
     * 
     * @param sql The SQL query (for logging purposes only)
     * @param param The parameter
     * @return A CompletableFuture that completes with a list of records
     */
    public CompletableFuture<List<Map<String, Object>>> queryForList(String sql, String param) {
        return dbConnectionFacade.queryForList(sql, param);
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
        return dbConnectionFacade.queryForList(sql, param1, param2);
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
        return dbConnectionFacade.updateAsync(sql, param1, param2);
    }
    
    /**
     * Update records with SQL - supports standard parameter replacement.
     * 
     * @param sql The SQL update statement
     * @param params The parameters
     * @return The number of rows affected
     */
    public int update(String sql, Object... params) {
        return dbConnectionFacade.update(sql, params);
    }
}
