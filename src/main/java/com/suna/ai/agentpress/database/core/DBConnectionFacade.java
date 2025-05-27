package com.Nubian.ai.agentpress.database.core;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.Nubian.ai.agentpress.database.legacy.LegacySqlOperations;
import com.Nubian.ai.agentpress.database.operations.DeleteOperations;
import com.Nubian.ai.agentpress.database.operations.InsertOperations;
import com.Nubian.ai.agentpress.database.operations.MessageOperations;
import com.Nubian.ai.agentpress.database.operations.QueryOperations;
import com.Nubian.ai.agentpress.database.operations.UpdateOperations;
import com.Nubian.ai.agentpress.model.Message;

/**
 * A facade for all database operations, delegating to specialized operation classes.
 */
@Service
public class DBConnectionFacade {

    private static final Logger logger = LoggerFactory.getLogger(DBConnectionFacade.class);

    private final MessageOperations messageOperations;
    private final QueryOperations queryOperations;
    private final InsertOperations insertOperations;
    private final UpdateOperations updateOperations;
    private final DeleteOperations deleteOperations;
    private final LegacySqlOperations legacySqlOperations;

    @Autowired
    public DBConnectionFacade(MessageOperations messageOperations, QueryOperations queryOperations,
                              InsertOperations insertOperations, UpdateOperations updateOperations,
                              DeleteOperations deleteOperations, LegacySqlOperations legacySqlOperations) {
        this.messageOperations = messageOperations;
        this.queryOperations = queryOperations;
        this.insertOperations = insertOperations;
        this.updateOperations = updateOperations;
        this.deleteOperations = deleteOperations;
        this.legacySqlOperations = legacySqlOperations;
        logger.debug("Initialized DBConnectionFacade with all operation components.");
    }

    // Message Operations
    public CompletableFuture<Message> insertMessage(Message message) {
        return messageOperations.insertMessage(message);
    }

    public CompletableFuture<List<Map<String, Object>>> getMessages(String threadId) {
        return messageOperations.getMessages(threadId);
    }

    public CompletableFuture<List<Map<String, Object>>> getLlmFormattedMessages(String threadId) {
        return messageOperations.getLlmFormattedMessages(threadId);
    }

    public CompletableFuture<Integer> deleteMessagesByType(String threadId, String messageType) {
        return messageOperations.deleteMessagesByType(threadId, messageType);
    }

    // Query Operations
    public CompletableFuture<List<Map<String, Object>>> queryForList(String tableName, Map<String, Object> conditions) {
        return queryOperations.queryForList(tableName, conditions);
    }

    public CompletableFuture<List<Map<String, Object>>> queryForList(String tableName, String fieldName, Object value) {
        return queryOperations.queryForList(tableName, fieldName, value);
    }

    public CompletableFuture<List<Map<String, Object>>> queryForList(String tableName, String field1, Object value1, String field2, Object value2) {
        return queryOperations.queryForList(tableName, field1, value1, field2, value2);
    }

    public <T> CompletableFuture<T> queryForObject(String sql, Class<T> resultClass, String param) {
        return queryOperations.queryForObject(sql, resultClass, param);
    }

    // Insert Operations
    public CompletableFuture<Map<String, Object>> insert(String tableName, Map<String, Object> data, boolean upsert) {
        return insertOperations.insert(tableName, data, upsert);
    }

    // Update Operations
    public CompletableFuture<Integer> update(String tableName, Map<String, Object> values, Map<String, Object> conditions) {
        return updateOperations.update(tableName, values, conditions);
    }


    // Delete Operations
    public CompletableFuture<Integer> delete(String tableName, Map<String, Object> conditions) {
        return deleteOperations.delete(tableName, conditions);
    }

    // Legacy Operations
    public CompletableFuture<List<Map<String, Object>>> queryForList(String sql, String param) {
        return legacySqlOperations.queryForList(sql, param);
    }

    public CompletableFuture<List<Map<String, Object>>> queryForList(String sql, String param1, String param2) {
        return legacySqlOperations.queryForList(sql, param1, param2);
    }

    public CompletableFuture<Integer> updateAsync(String sql, String param1, String param2) {
        return legacySqlOperations.updateAsync(sql, param1, param2);
    }

    public int update(String sql, Object... params) {
        return legacySqlOperations.update(sql, params);
    }
}
