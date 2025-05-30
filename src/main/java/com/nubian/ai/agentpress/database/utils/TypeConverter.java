package com.nubian.ai.agentpress.database.utils;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Utility class for converting database query results to various Java types.
 */
public class TypeConverter {

    private static final Logger logger = LoggerFactory.getLogger(TypeConverter.class);
    private final ObjectMapper objectMapper;

    public TypeConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Converts a database result map to the specified class type.
     * 
     * @param <T> The target type
     * @param result The database result as a Map
     * @param resultClass The class to convert to
     * @return The converted object, or null if conversion fails
     */
    public <T> T convertResult(Map<String, Object> result, Class<T> resultClass) {
        if (result == null || result.isEmpty()) {
            return null;
        }

        if (resultClass == String.class) {
            // If we're looking for a String, return the first column value as a String
            for (Object value : result.values()) {
                if (value != null) {
                    @SuppressWarnings("unchecked")
                    T castValue = (T) value.toString();
                    return castValue;
                }
            }
        } else if (resultClass == Integer.class) {
            // Handle Integer conversions
            for (Object value : result.values()) {
                if (value != null) {
                    if (value instanceof Number) {
                        @SuppressWarnings("unchecked")
                        T castValue = (T) Integer.valueOf(((Number) value).intValue());
                        return castValue;
                    } else {
                        try {
                            @SuppressWarnings("unchecked")
                            T castValue = (T) Integer.valueOf(value.toString());
                            return castValue;
                        } catch (NumberFormatException e) {
                            logger.error("Could not convert '{}' to Integer", value);
                        }
                    }
                }
            }
        } else if (resultClass == Long.class) {
            // Handle Long conversions
            for (Object value : result.values()) {
                if (value != null) {
                    if (value instanceof Number) {
                        @SuppressWarnings("unchecked")
                        T castValue = (T) Long.valueOf(((Number) value).longValue());
                        return castValue;
                    } else {
                        try {
                            @SuppressWarnings("unchecked")
                            T castValue = (T) Long.valueOf(value.toString());
                            return castValue;
                        } catch (NumberFormatException e) {
                            logger.error("Could not convert '{}' to Long", value);
                        }
                    }
                }
            }
        } else if (resultClass == Boolean.class) {
            // Handle Boolean conversions
            for (Object value : result.values()) {
                if (value != null) {
                    if (value instanceof Boolean) {
                        @SuppressWarnings("unchecked")
                        T castValue = (T) value;
                        return castValue;
                    } else {
                        @SuppressWarnings("unchecked")
                        T castValue = (T) Boolean.valueOf(value.toString());
                        return castValue;
                    }
                }
            }
        } else {
            // For complex objects, try JSON conversion
            try {
                String json = objectMapper.writeValueAsString(result);
                return objectMapper.readValue(json, resultClass);
            } catch (Exception e) {
                logger.error("Could not convert result to {}: {}", resultClass.getName(), e.getMessage());
            }
        }
        
        return null;
    }
}
