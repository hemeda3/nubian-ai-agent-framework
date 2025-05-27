package com.Nubian.ai.agentpress.database.parsers;

import java.util.regex.Pattern;

/**
 * Provides common SQL regex patterns for parsing and extracting information from SQL statements.
 */
public class SqlPatterns {

    // Pattern to extract table name from a SQL SELECT query, handling aliases
    public static final Pattern SELECT_TABLE_PATTERN = Pattern.compile("FROM\\s+([a-zA-Z0-9_]+)(\\s+[a-zA-Z0-9_]+)?");
    
    // Pattern to extract table name from a SQL UPDATE query
    public static final Pattern UPDATE_TABLE_PATTERN = Pattern.compile("UPDATE\\s+([a-zA-Z0-9_]+)");
    
    // Pattern to extract table name from a SQL INSERT query
    public static final Pattern INSERT_TABLE_PATTERN = Pattern.compile("INSERT\\s+INTO\\s+([a-zA-Z0-9_]+)");
    
    // Pattern to extract WHERE clause field
    public static final Pattern WHERE_FIELD_PATTERN = Pattern.compile("WHERE\\s+([a-zA-Z0-9_]+)\\s*=");
    
    // Pattern to extract SET field in UPDATE statements
    public static final Pattern SET_FIELD_PATTERN = Pattern.compile("SET\\s+([a-zA-Z0-9_]+)\\s*=");
    
    // Pattern to extract two fields from WHERE clause (for JOIN-like queries)
    public static final Pattern TWO_WHERE_FIELDS_PATTERN = Pattern.compile("WHERE\\s+([a-zA-Z0-9_]+)\\s*=\\s*\\?(?:\\s+AND\\s+([a-zA-Z0-9_]+)\\s*=\\s*\\?)?");

    private SqlPatterns() {
        // Private constructor to prevent instantiation
    }
}
