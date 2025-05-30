package com.nubian.ai.postgrest;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Builder for constructing PostgREST queries.
 *
 * @param <Schema> Database schema type
 * @param <Relation> Relation type (table or view)
 * @param <RelationName> Relation name
 * @param <Relationships> Relationships type
 */
public class PostgrestQueryBuilder<Schema, Relation, RelationName, Relationships> {
    protected URL url;
    protected Map<String, String> headers;
    protected String schema;
    protected PostgrestBuilder.AbortSignal signal;

    /**
     * Create a new PostgrestQueryBuilder.
     *
     * @param url The base URL
     * @param options Configuration options
     */
    public PostgrestQueryBuilder(URL url, PostgrestQueryBuilderOptions options) {
        this.url = url;
        this.headers = options.headers != null ? options.headers : new HashMap<>();
        this.schema = options.schema;
    }

    /**
     * Perform a SELECT query on the table or view.
     *
     * @param columns The columns to retrieve, separated by commas
     * @param options Named parameters
     * @return A PostgrestFilterBuilder for the query
     */
    @SuppressWarnings("unchecked")
    public <Query extends String, ResultOne> PostgrestFilterBuilder<Schema, Map<String, Object>, ResultOne[], RelationName, Relationships> select(
            Query columns,
            Map<String, Object> options) {
        
        String method = options != null && options.containsKey("head") && (Boolean) options.get("head") 
                ? "HEAD" : "GET";
        
        // Remove whitespaces except when quoted
        boolean quoted = false;
        StringBuilder cleanedColumns = new StringBuilder();
        String columnsStr = columns != null ? columns.toString() : "*";
        
        for (char c : columnsStr.toCharArray()) {
            if (Character.isWhitespace(c) && !quoted) {
                continue;
            }
            if (c == '"') {
                quoted = !quoted;
            }
            cleanedColumns.append(c);
        }
        
        // Create a new URL with the select parameter
        URL newUrl;
        try {
            String query = this.url.getQuery();
            String newQuery;
            
            if (query == null || query.isEmpty()) {
                newQuery = "select=" + cleanedColumns.toString();
            } else {
                newQuery = query + "&select=" + cleanedColumns.toString();
            }
            
            newUrl = new URL(this.url.getProtocol(), this.url.getHost(), this.url.getPort(), 
                    this.url.getPath() + "?" + newQuery);
        } catch (Exception e) {
            throw new RuntimeException("Error creating URL with select parameter: " + e.getMessage(), e);
        }
        
        // Set headers for count if requested
        Map<String, String> newHeaders = new HashMap<>(this.headers);
        if (options != null && options.containsKey("count")) {
            String count = (String) options.get("count");
            newHeaders.put("Prefer", "count=" + count);
        }
        
        // Create options for the filter builder
        PostgrestBuilder.PostgrestBuilderOptions builderOptions = new PostgrestBuilder.PostgrestBuilderOptions();
        builderOptions.url = newUrl;
        builderOptions.headers = newHeaders;
        builderOptions.schema = this.schema;
        builderOptions.method = method;
        builderOptions.allowEmpty = false;
        
        return new PostgrestFilterBuilder<>(builderOptions);
    }
    
    /**
     * Simplified version of select without options.
     * 
     * @param columns The columns to retrieve
     * @return A PostgrestFilterBuilder for the query
     */
    public <Query extends String, ResultOne> PostgrestFilterBuilder<Schema, Map<String, Object>, ResultOne[], RelationName, Relationships> select(
            Query columns) {
        return select(columns, null);
    }

    /**
     * Perform an INSERT into the table or view.
     *
     * @param values The values to insert
     * @param options Named parameters
     * @return A PostgrestFilterBuilder for the query
     */
    @SuppressWarnings("unchecked")
    public PostgrestFilterBuilder<Schema, Map<String, Object>, Object, RelationName, Relationships> insert(
            Object values,
            Map<String, Object> options) {
        
        String method = "POST";
        
        // Set Prefer headers
        Map<String, String> newHeaders = new HashMap<>(this.headers);
        StringBuilder preferHeader = new StringBuilder();
        
        if (newHeaders.containsKey("Prefer")) {
            preferHeader.append(newHeaders.get("Prefer"));
        }
        
        // Add count option if specified
        if (options != null && options.containsKey("count")) {
            if (preferHeader.length() > 0) {
                preferHeader.append(",");
            }
            preferHeader.append("count=").append(options.get("count"));
        }
        
        // Add defaultToNull option for bulk inserts
        if (options != null && options.containsKey("defaultToNull") && !(Boolean) options.get("defaultToNull")) {
            if (preferHeader.length() > 0) {
                preferHeader.append(",");
            }
            preferHeader.append("missing=default");
        }
        
        if (preferHeader.length() > 0) {
            newHeaders.put("Prefer", preferHeader.toString());
        }
        
        // Set columns parameter for bulk inserts
        URL newUrl = this.url;
        if (values instanceof Object[]) {
            Object[] valuesArray = (Object[]) values;
            if (valuesArray.length > 0 && valuesArray[0] instanceof Map) {
                // Extract unique column names from all objects
                Map<String, Boolean> columnsMap = new HashMap<>();
                for (Object obj : valuesArray) {
                    if (obj instanceof Map) {
                        Map<String, Object> map = (Map<String, Object>) obj;
                        for (String key : map.keySet()) {
                            columnsMap.put(key, true);
                        }
                    }
                }
                
                if (!columnsMap.isEmpty()) {
                    StringBuilder uniqueColumns = new StringBuilder();
                    for (String column : columnsMap.keySet()) {
                        if (uniqueColumns.length() > 0) {
                            uniqueColumns.append(",");
                        }
                        uniqueColumns.append("\"").append(column).append("\"");
                    }
                    
                    try {
                        String query = newUrl.getQuery();
                        String newQuery;
                        
                        if (query == null || query.isEmpty()) {
                            newQuery = "columns=" + uniqueColumns.toString();
                        } else {
                            newQuery = query + "&columns=" + uniqueColumns.toString();
                        }
                        
                        newUrl = new URL(newUrl.getProtocol(), newUrl.getHost(), newUrl.getPort(), 
                                newUrl.getPath() + "?" + newQuery);
                    } catch (Exception e) {
                        throw new RuntimeException("Error creating URL with columns parameter: " + e.getMessage(), e);
                    }
                }
            }
        }
        
        // Create options for the filter builder
        PostgrestBuilder.PostgrestBuilderOptions builderOptions = new PostgrestBuilder.PostgrestBuilderOptions();
        builderOptions.url = newUrl;
        builderOptions.headers = newHeaders;
        builderOptions.schema = this.schema;
        builderOptions.method = method;
        builderOptions.body = values;
        builderOptions.allowEmpty = false;
        
        return new PostgrestFilterBuilder<>(builderOptions);
    }
    
    /**
     * Simplified version of insert without options.
     * 
     * @param values The values to insert
     * @return A PostgrestFilterBuilder for the query
     */
    public PostgrestFilterBuilder<Schema, Map<String, Object>, Object, RelationName, Relationships> insert(
            Object values) {
        return insert(values, null);
    }

    /**
     * Perform an UPDATE on the table or view.
     *
     * @param values The values to update with
     * @param options Named parameters
     * @return A PostgrestFilterBuilder for the query
     */
    @SuppressWarnings("unchecked")
    public PostgrestFilterBuilder<Schema, Map<String, Object>, Object, RelationName, Relationships> update(
            Map<String, Object> values,
            Map<String, Object> options) {
        
        String method = "PATCH";
        
        // Set Prefer headers
        Map<String, String> newHeaders = new HashMap<>(this.headers);
        StringBuilder preferHeader = new StringBuilder();
        
        if (newHeaders.containsKey("Prefer")) {
            preferHeader.append(newHeaders.get("Prefer"));
        }
        
        // Add count option if specified
        if (options != null && options.containsKey("count")) {
            if (preferHeader.length() > 0) {
                preferHeader.append(",");
            }
            preferHeader.append("count=").append(options.get("count"));
        }
        
        if (preferHeader.length() > 0) {
            newHeaders.put("Prefer", preferHeader.toString());
        }
        
        // Create options for the filter builder
        PostgrestBuilder.PostgrestBuilderOptions builderOptions = new PostgrestBuilder.PostgrestBuilderOptions();
        builderOptions.url = this.url;
        builderOptions.headers = newHeaders;
        builderOptions.schema = this.schema;
        builderOptions.method = method;
        builderOptions.body = values;
        builderOptions.allowEmpty = false;
        
        return new PostgrestFilterBuilder<>(builderOptions);
    }
    
    /**
     * Simplified version of update without options.
     * 
     * @param values The values to update with
     * @return A PostgrestFilterBuilder for the query
     */
    public PostgrestFilterBuilder<Schema, Map<String, Object>, Object, RelationName, Relationships> update(
            Map<String, Object> values) {
        return update(values, null);
    }

    /**
     * Perform a DELETE on the table or view.
     *
     * @param options Named parameters
     * @return A PostgrestFilterBuilder for the query
     */
    @SuppressWarnings("unchecked")
    public PostgrestFilterBuilder<Schema, Map<String, Object>, Object, RelationName, Relationships> delete(
            Map<String, Object> options) {
        
        String method = "DELETE";
        
        // Set Prefer headers
        Map<String, String> newHeaders = new HashMap<>(this.headers);
        StringBuilder preferHeader = new StringBuilder();
        
        // Add count option if specified
        if (options != null && options.containsKey("count")) {
            preferHeader.append("count=").append(options.get("count"));
        }
        
        if (newHeaders.containsKey("Prefer")) {
            if (preferHeader.length() > 0) {
                preferHeader.insert(0, newHeaders.get("Prefer") + ",");
            } else {
                preferHeader.append(newHeaders.get("Prefer"));
            }
        }
        
        if (preferHeader.length() > 0) {
            newHeaders.put("Prefer", preferHeader.toString());
        }
        
        // Create options for the filter builder
        PostgrestBuilder.PostgrestBuilderOptions builderOptions = new PostgrestBuilder.PostgrestBuilderOptions();
        builderOptions.url = this.url;
        builderOptions.headers = newHeaders;
        builderOptions.schema = this.schema;
        builderOptions.method = method;
        builderOptions.allowEmpty = false;
        
        return new PostgrestFilterBuilder<>(builderOptions);
    }
    
    /**
     * Simplified version of delete without options.
     * 
     * @return A PostgrestFilterBuilder for the query
     */
    public PostgrestFilterBuilder<Schema, Map<String, Object>, Object, RelationName, Relationships> delete() {
        return delete(null);
    }
    
    /**
     * Configuration options for PostgrestQueryBuilder.
     */
    public static class PostgrestQueryBuilderOptions {
        public Map<String, String> headers;
        public String schema;
    }
}
