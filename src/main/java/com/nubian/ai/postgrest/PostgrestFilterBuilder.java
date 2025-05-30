package com.nubian.ai.postgrest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Builder for constructing filter operations in PostgREST queries.
 *
 * @param <Schema> Database schema type
 * @param <Row> Row type
 * @param <Result> Result type
 * @param <RelationName> Relation name
 * @param <Relationships> Relationships type
 */
public class PostgrestFilterBuilder<Schema, Row extends Map<String, Object>, Result, RelationName, Relationships>
        extends PostgrestTransformBuilder<Schema, Row, Result, RelationName, Relationships> {

    /**
     * Create a new PostgrestFilterBuilder
     */
    public PostgrestFilterBuilder(PostgrestBuilderOptions options) {
        super(options);
    }

    /**
     * Match only rows where `column` is equal to `value`.
     *
     * @param column The column to filter on
     * @param value The value to filter with
     * @return This builder instance
     */
    public PostgrestFilterBuilder<Schema, Row, Result, RelationName, Relationships> eq(String column, Object value) {
        this.url = appendSearchParam(this.url, column, "eq." + value);
        return this;
    }

    /**
     * Match only rows where `column` is not equal to `value`.
     *
     * @param column The column to filter on
     * @param value The value to filter with
     * @return This builder instance
     */
    public PostgrestFilterBuilder<Schema, Row, Result, RelationName, Relationships> neq(String column, Object value) {
        this.url = appendSearchParam(this.url, column, "neq." + value);
        return this;
    }
    
    /**
     * Filter on a JSON path using the specified operator and value.
     * This allows querying inside JSON columns like 'data->name' or 'sandbox->>id'.
     *
     * @param jsonPath The JSON path (e.g., "data->>name", "config->settings->>theme")
     * @param operator The operator to use (e.g., "eq", "neq", "gt")
     * @param value The value to filter with
     * @return This builder instance
     */
    public PostgrestFilterBuilder<Schema, Row, Result, RelationName, Relationships> filterJson(
            String jsonPath, String operator, Object value) {
        this.url = appendSearchParam(this.url, jsonPath, operator + "." + value);
        return this;
    }

    /**
     * Match only rows where `column` is greater than `value`.
     *
     * @param column The column to filter on
     * @param value The value to filter with
     * @return This builder instance
     */
    public PostgrestFilterBuilder<Schema, Row, Result, RelationName, Relationships> gt(String column, Object value) {
        this.url = appendSearchParam(this.url, column, "gt." + value);
        return this;
    }

    /**
     * Match only rows where `column` is greater than or equal to `value`.
     *
     * @param column The column to filter on
     * @param value The value to filter with
     * @return This builder instance
     */
    public PostgrestFilterBuilder<Schema, Row, Result, RelationName, Relationships> gte(String column, Object value) {
        this.url = appendSearchParam(this.url, column, "gte." + value);
        return this;
    }

    /**
     * Match only rows where `column` is less than `value`.
     *
     * @param column The column to filter on
     * @param value The value to filter with
     * @return This builder instance
     */
    public PostgrestFilterBuilder<Schema, Row, Result, RelationName, Relationships> lt(String column, Object value) {
        this.url = appendSearchParam(this.url, column, "lt." + value);
        return this;
    }

    /**
     * Match only rows where `column` is less than or equal to `value`.
     *
     * @param column The column to filter on
     * @param value The value to filter with
     * @return This builder instance
     */
    public PostgrestFilterBuilder<Schema, Row, Result, RelationName, Relationships> lte(String column, Object value) {
        this.url = appendSearchParam(this.url, column, "lte." + value);
        return this;
    }

    /**
     * Match only rows where `column` is included in the `values` array.
     *
     * @param column The column to filter on
     * @param values The values array to filter with
     * @return This builder instance
     */
    public PostgrestFilterBuilder<Schema, Row, Result, RelationName, Relationships> in(String column, Object[] values) {
        List<String> cleanedValues = new ArrayList<>();
        for (Object value : values) {
            String strValue = value.toString();
            // Handle postgrest reserved characters
            if (value instanceof String && strValue.matches(".*[,()].*")) {
                cleanedValues.add("\"" + strValue + "\"");
            } else {
                cleanedValues.add(strValue);
            }
        }
        
        String valuesStr = String.join(",", cleanedValues);
        this.url = appendSearchParam(this.url, column, "in.(" + valuesStr + ")");
        return this;
    }

    /**
     * Match only rows where `column` matches `pattern` case-sensitively.
     *
     * @param column The column to filter on
     * @param pattern The pattern to match with
     * @return This builder instance
     */
    public PostgrestFilterBuilder<Schema, Row, Result, RelationName, Relationships> like(String column, String pattern) {
        this.url = appendSearchParam(this.url, column, "like." + pattern);
        return this;
    }

    /**
     * Match only rows where `column` matches `pattern` case-insensitively.
     *
     * @param column The column to filter on
     * @param pattern The pattern to match with
     * @return This builder instance
     */
    public PostgrestFilterBuilder<Schema, Row, Result, RelationName, Relationships> ilike(String column, String pattern) {
        this.url = appendSearchParam(this.url, column, "ilike." + pattern);
        return this;
    }

    /**
     * Match only rows where each column in `query` keys is equal to its
     * associated value. Shorthand for multiple `.eq()`s.
     *
     * @param query The object to filter with, with column names as keys mapped
     * to their filter values
     * @return This builder instance
     */
    public PostgrestFilterBuilder<Schema, Row, Result, RelationName, Relationships> match(Map<String, Object> query) {
        for (Map.Entry<String, Object> entry : query.entrySet()) {
            this.url = appendSearchParam(this.url, entry.getKey(), "eq." + entry.getValue());
        }
        return this;
    }

    /**
     * Match only rows which satisfy at least one of the filters.
     *
     * @param filters The filters to use, following PostgREST syntax
     * @param options Named parameters
     * @return This builder instance
     */
    public PostgrestFilterBuilder<Schema, Row, Result, RelationName, Relationships> or(
            String filters,
            Map<String, String> options) {
        
        String referencedTable = options != null && options.containsKey("referencedTable") ? 
                options.get("referencedTable") : 
                options != null && options.containsKey("foreignTable") ? 
                        options.get("foreignTable") : null;
        
        String key = referencedTable != null ? referencedTable + ".or" : "or";
        
        this.url = appendSearchParam(this.url, key, "(" + filters + ")");
        return this;
    }

    /**
     * Match only rows which satisfy the filter.
     *
     * @param column The column to filter on
     * @param operator The operator to filter with
     * @param value The value to filter with
     * @return This builder instance
     */
    public PostgrestFilterBuilder<Schema, Row, Result, RelationName, Relationships> filter(
            String column,
            String operator,
            Object value) {
        
        this.url = appendSearchParam(this.url, column, operator + "." + value);
        return this;
    }

    /**
     * Append a search parameter to a URL.
     * 
     * @param url The URL
     * @param name The parameter name
     * @param value The parameter value
     * @return A new URL with the appended parameter
     */
    private URL appendSearchParam(URL url, String name, String value) {
        try {
            String encodedValue = URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
            String query = url.getQuery();
            String newQuery;
            
            if (query == null || query.isEmpty()) {
                newQuery = name + "=" + encodedValue;
            } else {
                newQuery = query + "&" + name + "=" + encodedValue;
            }
            
            return new URL(url.getProtocol(), url.getHost(), url.getPort(), 
                    url.getPath() + "?" + newQuery);
        } catch (Exception e) {
            throw new RuntimeException("Error appending search parameter: " + e.getMessage(), e);
        }
    }

    // Covariant Overrides for order methods from PostgrestTransformBuilder

    @Override
    public PostgrestFilterBuilder<Schema, Row, Result, RelationName, Relationships> order(
            String column, 
            Map<String, Object> options) {
        super.order(column, options);
        return this;
    }

    @Override
    public PostgrestFilterBuilder<Schema, Row, Result, RelationName, Relationships> order(String column) {
        super.order(column);
        return this;
    }

    @Override
    public PostgrestFilterBuilder<Schema, Row, Result, RelationName, Relationships> order(String column, boolean ascending) {
        super.order(column, ascending);
        return this;
    }
}
