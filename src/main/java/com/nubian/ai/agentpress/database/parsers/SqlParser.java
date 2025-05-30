package com.nubian.ai.agentpress.database.parsers;

import java.util.regex.Matcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for parsing SQL statements and extracting relevant information.
 */
public class SqlParser {

    private static final Logger logger = LoggerFactory.getLogger(SqlParser.class);

    private SqlParser() {
        // Private constructor to prevent instantiation
    }

    /**
     * Extract the table name from a SQL statement.
     * 
     * @param sql The SQL statement
     * @return The table name, or null if not found
     */
    public static String extractTableName(String sql) {
        if (sql == null || sql.isEmpty()) {
            return null;
        }
        
        String upperSql = sql.toUpperCase();
        
        // Handle SELECT queries
        Matcher selectMatcher = SqlPatterns.SELECT_TABLE_PATTERN.matcher(upperSql);
        if (selectMatcher.find()) {
            return selectMatcher.group(1);
        }
        
        // Handle UPDATE queries
        Matcher updateMatcher = SqlPatterns.UPDATE_TABLE_PATTERN.matcher(upperSql);
        if (updateMatcher.find()) {
            return updateMatcher.group(1);
        }
        
        // Handle INSERT queries
        Matcher insertMatcher = SqlPatterns.INSERT_TABLE_PATTERN.matcher(upperSql);
        if (insertMatcher.find()) {
            return insertMatcher.group(1);
        }
        
        logger.warn("Could not extract table name from SQL: {}", sql);
        return null;
    }

    /**
     * Extract the field name from the WHERE clause of a SQL statement.
     * 
     * @param sql The SQL statement
     * @return The field name, or null if not found
     */
    public static String extractWhereField(String sql) {
        if (sql == null || sql.isEmpty()) {
            return null;
        }
        
        Matcher matcher = SqlPatterns.WHERE_FIELD_PATTERN.matcher(sql.toUpperCase());
        if (matcher.find()) {
            return matcher.group(1);
        }
        logger.warn("Could not extract WHERE field from SQL: {}", sql);
        return null;
    }

    /**
     * Extract two field names from the WHERE clause of a SQL statement.
     * This is typically used for queries with two conditions.
     * 
     * @param sql The SQL statement
     * @return An array containing the two field names, or null if not found
     */
    public static String[] extractTwoWhereFields(String sql) {
        if (sql == null || sql.isEmpty()) {
            return null;
        }
        
        Matcher matcher = SqlPatterns.TWO_WHERE_FIELDS_PATTERN.matcher(sql.toUpperCase());
        if (matcher.find()) {
            String[] fields = new String[2];
            fields[0] = matcher.group(1);
            fields[1] = matcher.group(2); // This might be null if only one field is present
            return fields;
        }
        logger.warn("Could not extract two WHERE fields from SQL: {}", sql);
        return null;
    }

    /**
     * Extract the field name from the SET clause of an UPDATE SQL statement.
     * 
     * @param sql The SQL statement
     * @return The field name, or null if not found
     */
    public static String extractSetField(String sql) {
        if (sql == null || sql.isEmpty()) {
            return null;
        }
        
        Matcher matcher = SqlPatterns.SET_FIELD_PATTERN.matcher(sql.toUpperCase());
        if (matcher.find()) {
            return matcher.group(1);
        }
        logger.warn("Could not extract SET field from SQL: {}", sql);
        return null;
    }
}
