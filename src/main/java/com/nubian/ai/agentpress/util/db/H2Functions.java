package com.nubian.ai.agentpress.util.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * H2 database functions for AgentPress.
 * These functions provide database-level operations for message processing and context management.
 */
public class H2Functions {
    private static final Logger logger = LoggerFactory.getLogger(H2Functions.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // Context window limits
    private static final int MAX_CONTEXT_TOKENS = 8000;
    private static final int SUMMARY_TOKEN_THRESHOLD = 6000;
    private static final int ESTIMATED_TOKENS_PER_CHAR = 4; // Rough estimate for token counting
    
    /**
     * Get LLM-formatted messages for a thread with context window management.
     * This function handles summarization of older messages when the context becomes too large.
     * 
     * @param conn Database connection
     * @param threadId Thread ID to get messages for
     * @return ResultSet containing formatted messages
     * @throws SQLException if database error occurs
     */
    @SuppressWarnings("unchecked")
    public static ResultSet getLlmFormattedMessages(Connection conn, String threadId) throws SQLException {
        // TODO: Review and refactor this method for robustness and efficiency, especially the summarization logic
        // and token estimation. Consider using a proper tokenizer and optimizing database interactions.
        logger.debug("Getting LLM-formatted messages for thread: {}", threadId);
        
        try {
            // Get all messages for the thread ordered by creation time
            String sql = """
                SELECT message_id, thread_id, type, content, is_llm_message, metadata, created_at, updated_at
                FROM messages 
                WHERE thread_id = ? 
                ORDER BY created_at ASC
                """;
            
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, threadId);
            ResultSet rs = stmt.executeQuery();
            
            List<Map<String, Object>> allMessages = new ArrayList<>();
            
            // Process each message
            while (rs.next()) {
                Map<String, Object> message = new HashMap<>();
                message.put("message_id", rs.getString("message_id"));
                message.put("thread_id", rs.getString("thread_id"));
                message.put("type", rs.getString("type"));
                message.put("content", rs.getString("content"));
                message.put("is_llm_message", rs.getBoolean("is_llm_message"));
                message.put("metadata", rs.getString("metadata"));
                message.put("created_at", rs.getTimestamp("created_at"));
                message.put("updated_at", rs.getTimestamp("updated_at"));
                allMessages.add(message);
            }
            
            rs.close();
            stmt.close();
            
            // Apply context window management
            List<Map<String, Object>> formattedMessages = applyContextWindowManagement(conn, threadId, allMessages);
            
            // Convert back to ResultSet format for compatibility
            return createResultSetFromMessages(conn, formattedMessages);
            
        } catch (Exception e) {
            logger.error("Error getting LLM-formatted messages for thread {}: {}", threadId, e.getMessage(), e);
            throw new SQLException("Error processing messages: " + e.getMessage(), e);
        }
    }
    
    /**
     * Estimate token count for a given text.
     * This is a rough approximation - in production you'd use a proper tokenizer.
     * 
     * @param text Text to estimate tokens for
     * @return Estimated token count
     */
    private static int estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        // Rough estimation: average 4 characters per token
        // This is a simplification - real tokenizers are more complex
        return Math.max(1, text.length() / ESTIMATED_TOKENS_PER_CHAR);
    }
    
    /**
     * Apply context window management to a list of messages.
     * This function prioritizes recent messages and summarizes older ones if necessary
     * to fit within the MAX_CONTEXT_TOKENS limit.
     * 
     * @param conn Database connection
     * @param threadId Thread ID
     * @param allMessages List of all messages in chronological order
     * @return List of messages optimized for the LLM context window
     */
    private static List<Map<String, Object>> applyContextWindowManagement(
            Connection conn, String threadId, List<Map<String, Object>> allMessages) {
        
        List<Map<String, Object>> resultMessages = new ArrayList<>();
        int currentTokens = 0;
        
        // Add recent messages first, in reverse chronological order
        // Then reverse them to maintain chronological order for the LLM
        List<Map<String, Object>> recentMessagesBuffer = new ArrayList<>();
        for (int i = allMessages.size() - 1; i >= 0; i--) {
            Map<String, Object> message = allMessages.get(i);
            String content = (String) message.get("content");
            int messageTokens = estimateTokenCount(content);
            
            // If adding this message exceeds the max context, stop adding recent messages
            // and prepare to summarize the rest.
            if (currentTokens + messageTokens > MAX_CONTEXT_TOKENS) {
                break;
            }
            
            recentMessagesBuffer.add(0, message); // Add to the beginning to maintain chronological order
            currentTokens += messageTokens;
        }
        
        // If all messages fit, return them directly
        if (recentMessagesBuffer.size() == allMessages.size()) {
            logger.debug("All {} messages fit within context window ({} tokens)", allMessages.size(), currentTokens);
            return allMessages;
        }
        
        // Determine which messages need summarization
        List<Map<String, Object>> messagesToSummarize = allMessages.subList(0, allMessages.size() - recentMessagesBuffer.size());
        
        if (!messagesToSummarize.isEmpty()) {
            // Create a summary message from older messages
            StringBuilder summaryContent = new StringBuilder();
            summaryContent.append("CONVERSATION SUMMARY:\n");
            summaryContent.append("This is a summary of ").append(messagesToSummarize.size()).append(" earlier messages in this conversation.\n\n");
            
            // Extract key information from old messages, truncating content
            for (Map<String, Object> msg : messagesToSummarize) {
                String type = (String) msg.get("type");
                String content = (String) msg.get("content");
                boolean isLlm = (Boolean) msg.get("is_llm_message");
                
                if ("user".equals(type)) {
                    summaryContent.append("User: ").append(truncateContent(content, 100)).append("\n");
                } else if ("assistant".equals(type) || isLlm) {
                    summaryContent.append("Assistant: ").append(truncateContent(content, 100)).append("\n");
                } else if ("tool_result".equals(type)) {
                    summaryContent.append("Tool Result: ").append(truncateContent(content, 50)).append("\n");
                }
            }
            
            summaryContent.append("\n--- END SUMMARY ---\n");
            
            // Create summary message map
            Map<String, Object> summaryMessage = new HashMap<>();
            summaryMessage.put("message_id", "summary_" + threadId + "_" + System.currentTimeMillis());
            summaryMessage.put("thread_id", threadId);
            summaryMessage.put("type", "summary");
            summaryMessage.put("content", summaryContent.toString());
            summaryMessage.put("is_llm_message", false);
            summaryMessage.put("metadata", "{\"token_count\": " + estimateTokenCount(summaryContent.toString()) + "}");
            summaryMessage.put("created_at", messagesToSummarize.get(0).get("created_at"));
            summaryMessage.put("updated_at", new java.sql.Timestamp(System.currentTimeMillis()));
            
            // Check if summary itself exceeds remaining token budget
            int summaryTokens = estimateTokenCount(summaryContent.toString());
            if (currentTokens + summaryTokens > MAX_CONTEXT_TOKENS) {
                // If summary is too long, truncate it further or adjust strategy
                // For now, we'll just log a warning and proceed, as this is a rough estimate
                logger.warn("Generated summary for thread {} is too large ({} tokens) to fit remaining context. Truncating if necessary.", threadId, summaryTokens);
                // A more sophisticated approach might involve a recursive summarization or more aggressive truncation
                summaryMessage.put("content", truncateContent(summaryContent.toString(), (MAX_CONTEXT_TOKENS - currentTokens) * ESTIMATED_TOKENS_PER_CHAR));
                summaryMessage.put("metadata", "{\"token_count\": " + estimateTokenCount((String)summaryMessage.get("content")) + "}");
            }
            
            // Store the summary in the database for future use
            storeSummaryMessage(conn, summaryMessage);
            
            resultMessages.add(summaryMessage);
            logger.info("Summarized {} older messages into summary for thread {}", messagesToSummarize.size(), threadId);
        }
        
        resultMessages.addAll(recentMessagesBuffer);
        
        return resultMessages;
    }
    
    /**
     * Truncate content to a maximum length for summaries.
     * 
     * @param content Content to truncate
     * @param maxLength Maximum length
     * @return Truncated content
     */
    private static String truncateContent(String content, int maxLength) {
        if (content == null || content.length() <= maxLength) {
            return content;
        }
        // Ensure we don't cut in the middle of a word if possible, but prioritize length
        String truncated = content.substring(0, maxLength);
        if (content.length() > maxLength && !content.substring(maxLength).isEmpty() && !Character.isWhitespace(content.charAt(maxLength))) {
            int lastSpace = truncated.lastIndexOf(' ');
            if (lastSpace != -1) {
                truncated = truncated.substring(0, lastSpace);
            }
        }
        return truncated + "...";
    }
    
    /**
     * Store a summary message in the database.
     * 
     * @param conn Database connection
     * @param summaryMessage Summary message to store
     */
    private static void storeSummaryMessage(Connection conn, Map<String, Object> summaryMessage) {
        try {
            // Check if a summary message with the same ID already exists to avoid duplicates
            String checkSql = "SELECT COUNT(*) FROM messages WHERE message_id = ?";
            PreparedStatement checkStmt = conn.prepareStatement(checkSql);
            checkStmt.setString(1, (String) summaryMessage.get("message_id"));
            ResultSet rs = checkStmt.executeQuery();
            rs.next();
            boolean exists = rs.getInt(1) > 0;
            rs.close();
            checkStmt.close();
            
            if (exists) {
                logger.debug("Summary message {} already exists, skipping insertion.", summaryMessage.get("message_id"));
                return;
            }
            
            String sql = """
                INSERT INTO messages (message_id, thread_id, type, content, is_llm_message, metadata, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
            
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, (String) summaryMessage.get("message_id"));
            stmt.setString(2, (String) summaryMessage.get("thread_id"));
            stmt.setString(3, (String) summaryMessage.get("type"));
            stmt.setString(4, (String) summaryMessage.get("content"));
            stmt.setBoolean(5, (Boolean) summaryMessage.get("is_llm_message"));
            stmt.setString(6, (String) summaryMessage.get("metadata"));
            stmt.setTimestamp(7, (java.sql.Timestamp) summaryMessage.get("created_at"));
            stmt.setTimestamp(8, (java.sql.Timestamp) summaryMessage.get("updated_at"));
            
            stmt.executeUpdate();
            stmt.close();
            
            logger.debug("Stored summary message: {}", summaryMessage.get("message_id"));
            
        } catch (SQLException e) {
            logger.error("Error storing summary message: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Create a ResultSet from a list of message maps.
     * This is a simplified implementation for H2 compatibility.
     * 
     * @param conn Database connection
     * @param messages List of messages
     * @return ResultSet containing the messages
     */
    private static ResultSet createResultSetFromMessages(Connection conn, List<Map<String, Object>> messages) 
            throws SQLException {
        
        // Create a temporary table to hold the results
        String tempTableName = "temp_messages_" + System.currentTimeMillis();
        
        String createTempTable = """
            CREATE LOCAL TEMPORARY TABLE %s (
                message_id VARCHAR(255),
                thread_id VARCHAR(255),
                type VARCHAR(50),
                content CLOB,
                is_llm_message BOOLEAN,
                metadata CLOB,
                created_at TIMESTAMP,
                updated_at TIMESTAMP
            )
            """.formatted(tempTableName);
        
        PreparedStatement createStmt = conn.prepareStatement(createTempTable);
        createStmt.executeUpdate();
        createStmt.close();
        
        // Insert messages into temp table
        String insertSql = """
            INSERT INTO %s (message_id, thread_id, type, content, is_llm_message, metadata, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.formatted(tempTableName);
        
        PreparedStatement insertStmt = conn.prepareStatement(insertSql);
        
        for (Map<String, Object> message : messages) {
            insertStmt.setString(1, (String) message.get("message_id"));
            insertStmt.setString(2, (String) message.get("thread_id"));
            insertStmt.setString(3, (String) message.get("type"));
            insertStmt.setString(4, (String) message.get("content"));
            insertStmt.setBoolean(5, (Boolean) message.get("is_llm_message"));
            insertStmt.setString(6, (String) message.get("metadata"));
            insertStmt.setTimestamp(7, (java.sql.Timestamp) message.get("created_at"));
            insertStmt.setTimestamp(8, (java.sql.Timestamp) message.get("updated_at"));
            insertStmt.addBatch();
        }
        
        insertStmt.executeBatch();
        insertStmt.close();
        
        // Return ResultSet from temp table
        String selectSql = "SELECT * FROM " + tempTableName + " ORDER BY created_at";
        PreparedStatement selectStmt = conn.prepareStatement(selectSql);
        return selectStmt.executeQuery();
    }
}
