package com.nubian.ai.agentpress.util.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Centralized logging utility.
 * Provides structured logging, correlation IDs, and context management.
 */
public final class LoggingUtils {
    private static final Logger rootLogger = LoggerFactory.getLogger(LoggingUtils.class);
    
    /**
     * MDC key for request correlation ID.
     */
    public static final String REQUEST_ID_KEY = "requestId";
    
    /**
     * MDC key for thread ID.
     */
    public static final String THREAD_ID_KEY = "threadId";
    
    /**
     * MDC key for user ID.
     */
    public static final String USER_ID_KEY = "userId";

    /**
     * Get a logger for a specific class.
     *
     * @param clazz The class to get a logger for
     * @return The logger instance
     */
    public static Logger getLogger(Class<?> clazz) {
        return LoggerFactory.getLogger(clazz);
    }
    
    /**
     * Generate a new request ID.
     *
     * @return A unique request ID
     */
    public static String generateRequestId() {
        return UUID.randomUUID().toString();
    }
    
    /**
     * Set the request ID in the MDC.
     *
     * @param requestId The request ID to set
     */
    public static void setRequestId(String requestId) {
        MDC.put(REQUEST_ID_KEY, requestId);
    }
    
    /**
     * Get the current request ID from MDC.
     *
     * @return The current request ID, or null if not set
     */
    public static String getRequestId() {
        return MDC.get(REQUEST_ID_KEY);
    }
    
    /**
     * Set the user ID in the MDC.
     *
     * @param userId The user ID to set
     */
    public static void setUserId(String userId) {
        MDC.put(USER_ID_KEY, userId);
    }
    
    /**
     * Set the thread ID in the MDC.
     *
     * @param threadId The thread ID to set
     */
    public static void setThreadId(String threadId) {
        MDC.put(THREAD_ID_KEY, threadId);
    }
    
    /**
     * Clear all MDC values.
     */
    public static void clearMDC() {
        MDC.clear();
    }
    
    /**
     * Add a new key-value pair to the MDC.
     *
     * @param key The key
     * @param value The value
     */
    public static void putMDC(String key, String value) {
        MDC.put(key, value);
    }
    
    /**
     * Execute a callable with context information in MDC.
     *
     * @param context The context information to add to MDC
     * @param callable The callable to execute
     * @param <T> The return type of the callable
     * @return The result of the callable
     * @throws Exception If the callable throws an exception
     */
    public static <T> T withContext(Map<String, String> context, Callable<T> callable) throws Exception {
        // Store the original MDC
        Map<String, String> originalContext = MDC.getCopyOfContextMap();
        
        try {
            // Set the new context
            if (context != null) {
                context.forEach(MDC::put);
            }
            
            // Execute the callable
            return callable.call();
        } finally {
            // Restore the original MDC
            if (originalContext != null) {
                MDC.setContextMap(originalContext);
            } else {
                MDC.clear();
            }
        }
    }
    
    /**
     * Execute a supplier with a request ID in MDC.
     *
     * @param supplier The supplier to execute
     * @param <T> The return type of the supplier
     * @return The result of the supplier
     */
    public static <T> T withRequestId(Supplier<T> supplier) {
        String requestId = generateRequestId();
        
        // Store the original MDC
        Map<String, String> originalContext = MDC.getCopyOfContextMap();
        
        try {
            // Set the request ID
            MDC.put(REQUEST_ID_KEY, requestId);
            
            // Execute the supplier
            return supplier.get();
        } finally {
            // Restore the original MDC
            if (originalContext != null) {
                MDC.setContextMap(originalContext);
            } else {
                MDC.clear();
            }
        }
    }
    
    /**
     * Create a structured log entry with additional metadata.
     *
     * @param logger The logger to use
     * @param level The log level
     * @param message The log message
     * @param metadata Additional metadata to include in the log
     */
    public static void logWithMetadata(Logger logger, LogLevel level, String message, Map<String, Object> metadata) {
        if (!isEnabled(logger, level)) {
            return;
        }
        
        // Add timestamp to metadata
        metadata.put("timestamp", Instant.now().toString());
        
        // Add request ID if available
        String requestId = MDC.get(REQUEST_ID_KEY);
        if (requestId != null) {
            metadata.put("requestId", requestId);
        }
        
        // Add thread ID if available
        String threadId = MDC.get(THREAD_ID_KEY);
        if (threadId != null) {
            metadata.put("threadId", threadId);
        }
        
        // Log with metadata
        String formattedMessage = String.format("%s | metadata=%s", message, metadata);
        log(logger, level, formattedMessage);
    }
    
    /**
     * Log at the specified level.
     *
     * @param logger The logger to use
     * @param level The log level
     * @param message The log message
     */
    public static void log(Logger logger, LogLevel level, String message) {
        switch (level) {
            case TRACE:
                logger.trace(message);
                break;
            case DEBUG:
                logger.debug(message);
                break;
            case INFO:
                logger.info(message);
                break;
            case WARN:
                logger.warn(message);
                break;
            case ERROR:
                logger.error(message);
                break;
        }
    }
    
    /**
     * Check if the specified log level is enabled.
     *
     * @param logger The logger to check
     * @param level The log level to check
     * @return true if the log level is enabled, false otherwise
     */
    public static boolean isEnabled(Logger logger, LogLevel level) {
        switch (level) {
            case TRACE:
                return logger.isTraceEnabled();
            case DEBUG:
                return logger.isDebugEnabled();
            case INFO:
                return logger.isInfoEnabled();
            case WARN:
                return logger.isWarnEnabled();
            case ERROR:
                return logger.isErrorEnabled();
            default:
                return false;
        }
    }
    
    /**
     * Log an exception with context information.
     *
     * @param logger The logger to use
     * @param level The log level
     * @param message The log message
     * @param throwable The exception to log
     * @param metadata Additional metadata to include in the log
     */
    public static void logException(Logger logger, LogLevel level, String message, Throwable throwable, Map<String, Object> metadata) {
        if (!isEnabled(logger, level)) {
            return;
        }
        
        // Add timestamp to metadata
        metadata.put("timestamp", Instant.now().toString());
        
        // Add request ID if available
        String requestId = MDC.get(REQUEST_ID_KEY);
        if (requestId != null) {
            metadata.put("requestId", requestId);
        }
        
        // Log with metadata
        String formattedMessage = String.format("%s | metadata=%s", message, metadata);
        
        switch (level) {
            case TRACE:
                logger.trace(formattedMessage, throwable);
                break;
            case DEBUG:
                logger.debug(formattedMessage, throwable);
                break;
            case INFO:
                logger.info(formattedMessage, throwable);
                break;
            case WARN:
                logger.warn(formattedMessage, throwable);
                break;
            case ERROR:
                logger.error(formattedMessage, throwable);
                break;
        }
    }
    
    /**
     * Private constructor to prevent instantiation.
     */
    private LoggingUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    
    /**
     * Log levels.
     */
    public enum LogLevel {
        TRACE,
        DEBUG,
        INFO,
        WARN,
        ERROR
    }
}
