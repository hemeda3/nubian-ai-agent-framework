package com.Nubian.ai.postgrest;

import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Builder for handling transformations in PostgREST queries.
 *
 * @param <Schema> Database schema type
 * @param <Row> Row type
 * @param <Result> Result type
 * @param <RelationName> Relation name
 * @param <Relationships> Relationships type
 */
public class PostgrestTransformBuilder<Schema, Row extends Map<String, Object>, Result, RelationName, Relationships>
        extends PostgrestBuilder<Result> {

    /**
     * Create a new PostgrestTransformBuilder
     */
    public PostgrestTransformBuilder(PostgrestBuilderOptions options) {
        super(options);
    }

    /**
     * Perform a SELECT on the query result.
     *
     * By default, `.insert()`, `.update()`, `.upsert()`, and `.delete()` do not
     * return modified rows. By calling this method, modified rows are returned in
     * `data`.
     *
     * @param columns The columns to retrieve, separated by commas
     * @return A new PostgrestTransformBuilder instance
     */
    @SuppressWarnings("unchecked")
    public <Query extends String, NewResultOne> PostgrestTransformBuilder<Schema, Row, NewResultOne[], RelationName, Relationships> select(
            Query columns) {
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
        
        this.url.getQuery(); // This will initialize the query parameters
        this.url.getRef();   // This ensures URL object is ready
        
        Map<String, String> newParams = new java.util.HashMap<>();
        newParams.put("select", cleanedColumns.toString());
        
        // Create a new URL with the updated parameters
        URL newUrl = replaceQueryParam(this.url, "select", cleanedColumns.toString());
        
        // Update headers to include return=representation
        Map<String, String> newHeaders = new java.util.HashMap<>(this.headers);
        String prefer = newHeaders.getOrDefault("Prefer", "");
        if (!prefer.isEmpty()) {
            prefer += ",";
        }
        prefer += "return=representation";
        newHeaders.put("Prefer", prefer);
        
        // Create options for the new builder
        PostgrestBuilderOptions options = new PostgrestBuilderOptions();
        options.url = newUrl;
        options.headers = newHeaders;
        options.schema = this.schema;
        options.method = this.method;
        options.body = this.body;
        options.allowEmpty = this.allowEmpty;
        options.httpClient = this.httpClient;
        options.objectMapper = this.objectMapper;
        options.signal = this.signal;
        
        return  new PostgrestTransformBuilder<>(options);
    }
    
    /**
     * Order the query result by `column`.
     *
     * @param column The column to order by
     * @param options Named parameters for ordering
     * @return This builder instance
     */
    public PostgrestTransformBuilder<Schema, Row, Result, RelationName, Relationships> order(
            String column, 
            Map<String, Object> options) {
        
        boolean ascending = options != null && options.containsKey("ascending") ? 
                (Boolean) options.get("ascending") : true;
        
        Boolean nullsFirst = options != null && options.containsKey("nullsFirst") ? 
                (Boolean) options.get("nullsFirst") : null;
        
        String referencedTable = options != null && options.containsKey("referencedTable") ? 
                (String) options.get("referencedTable") : 
                options != null && options.containsKey("foreignTable") ? 
                        (String) options.get("foreignTable") : null;
        
        String key = referencedTable != null ? referencedTable + ".order" : "order";
        
        // Get existing order parameter
        String existingOrder = getQueryParam(this.url, key);
        
        // Build the new order value
        StringBuilder orderValue = new StringBuilder();
        if (existingOrder != null && !existingOrder.isEmpty()) {
            orderValue.append(existingOrder).append(",");
        }
        
        orderValue.append(column).append(".").append(ascending ? "asc" : "desc");
        
        if (nullsFirst != null) {
            orderValue.append(nullsFirst ? ".nullsfirst" : ".nullslast");
        }
        
        // Set the new URL with updated order parameter
        this.url = replaceQueryParam(this.url, key, orderValue.toString());
        
        return this;
    }
    
    /**
     * Limit the query result by `count`.
     *
     * @param count The maximum number of rows to return
     * @param options Named parameters
     * @return This builder instance
     */
    public PostgrestTransformBuilder<Schema, Row, Result, RelationName, Relationships> limit(
            int count, 
            Map<String, String> options) {
        
        String referencedTable = options != null && options.containsKey("referencedTable") ? 
                options.get("referencedTable") : 
                options != null && options.containsKey("foreignTable") ? 
                        options.get("foreignTable") : null;
        
        String key = referencedTable != null ? referencedTable + ".limit" : "limit";
        
        // Set the new URL with updated limit parameter
        this.url = replaceQueryParam(this.url, key, Integer.toString(count));
        
        return this;
    }
    
    /**
     * Set the AbortSignal for the fetch request.
     *
     * @param signal The AbortSignal to use for the fetch request
     * @return This builder instance
     */
    public PostgrestTransformBuilder<Schema, Row, Result, RelationName, Relationships> abortSignal(AbortSignal signal) {
        this.signal = signal;
        return this;
    }
    
    /**
     * Order the result by one or more columns.
     *
     * @param column The column to order by
     * @return This builder instance
     */
    public PostgrestTransformBuilder<Schema, Row, Result, RelationName, Relationships> order(String column) {
        return order(column, true);
    }
    
    /**
     * Order the result by one or more columns with specified direction.
     *
     * @param column The column to order by
     * @param ascending If true, order is ascending; if false, order is descending
     * @return This builder instance
     */
    public PostgrestTransformBuilder<Schema, Row, Result, RelationName, Relationships> order(String column, boolean ascending) {
        try {
            // Format the column with .desc if descending order is requested
            String formattedColumn = ascending ? column : column + ".desc";
            String encodedColumn = URLEncoder.encode(formattedColumn, StandardCharsets.UTF_8.toString());
            String query = this.url.getQuery();
            String newQuery;
            
            if (query == null || query.isEmpty()) {
                newQuery = "order=" + encodedColumn;
            } else {
                newQuery = query + "&order=" + encodedColumn;
            }
            
            this.url = new URL(this.url.getProtocol(), this.url.getHost(), this.url.getPort(), 
                    this.url.getPath() + "?" + newQuery);
            
            return this;
        } catch (Exception e) {
            throw new RuntimeException("Error setting order: " + e.getMessage(), e);
        }
    }
    
    /**
     * Return `data` as a single object instead of an array of objects.
     *
     * Query result must be zero or one row (e.g. using `.limit(1)`), otherwise
     * this returns an error.
     * 
     * @return A new PostgrestBuilder for a possibly null single result
     */
    @SuppressWarnings("unchecked")
    public <ResultOne> PostgrestBuilder<ResultOne> maybeSingle() {
        Map<String, String> newHeaders = new java.util.HashMap<>(this.headers);
        
        if ("GET".equals(this.method)) {
            newHeaders.put("Accept", "application/json");
        } else {
            newHeaders.put("Accept", "application/vnd.pgrst.object+json");
        }
        
        PostgrestBuilderOptions options = new PostgrestBuilderOptions();
        options.url = this.url;
        options.headers = newHeaders;
        options.schema = this.schema;
        options.method = this.method;
        options.body = this.body;
        options.allowEmpty = this.allowEmpty;
        options.httpClient = this.httpClient;
        options.objectMapper = this.objectMapper;
        options.signal = this.signal;
        
        PostgrestBuilder<ResultOne> builder = new PostgrestBuilder<>(options);
        builder.isMaybeSingle = true;
        
        return builder;
    }
    
    /**
     * Gets a query parameter from a URL.
     * 
     * @param url The URL
     * @param name The parameter name
     * @return The parameter value, or null if not found
     */
    private String getQueryParam(URL url, String name) {
        String query = url.getQuery();
        if (query == null || query.isEmpty()) {
            return null;
        }
        
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0 && pair.substring(0, idx).equals(name)) {
                return pair.substring(idx + 1);
            }
        }
        
        return null;
    }
    
    /**
     * Replaces a query parameter in a URL.
     * 
     * @param url The URL
     * @param name The parameter name
     * @param value The parameter value
     * @return A new URL with the updated parameter
     */
    private URL replaceQueryParam(URL url, String name, String value) {
        try {
            String query = url.getQuery();
            if (query == null) {
                query = name + "=" + value;
            } else {
                boolean found = false;
                StringBuilder newQuery = new StringBuilder();
                String[] pairs = query.split("&");
                
                for (String pair : pairs) {
                    int idx = pair.indexOf("=");
                    if (idx > 0) {
                        String k = pair.substring(0, idx);
                        if (k.equals(name)) {
                            if (found) {
                                continue;
                            }
                            found = true;
                            newQuery.append(name).append("=").append(value);
                        } else {
                            if (newQuery.length() > 0) {
                                newQuery.append("&");
                            }
                            newQuery.append(pair);
                        }
                    }
                }
                
                if (!found) {
                    if (newQuery.length() > 0) {
                        newQuery.append("&");
                    }
                    newQuery.append(name).append("=").append(value);
                }
                
                query = newQuery.toString();
            }
            
            return new URL(url.getProtocol(), url.getHost(), url.getPort(), 
                    url.getPath() + "?" + query);
        } catch (Exception e) {
            throw new RuntimeException("Error replacing query parameter: " + e.getMessage(), e);
        }
    }
}
