package com.nubian.ai.agentpress.sandbox.tool;

import com.nubian.ai.agentpress.annotations.ToolFunction;
import com.nubian.ai.agentpress.model.ToolResult;
import com.nubian.ai.agentpress.sandbox.model.FileInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A more resilient version of FileTool that gracefully handles errors
 * and dependency issues.
 */
public class ResilientFileTool extends FileTool {
    
    /**
     * Initialize the resilient file tool for a specific project.
     *
     * @param projectId The project ID
     */
    public ResilientFileTool(String projectId) {
        super(projectId);
    }
    
    /**
     * Check if essential dependencies are available.
     * 
     * @throws IllegalStateException if dependencies are missing
     */
    protected void checkDependencies() {
        if (dbConnection == null) {
            throw new IllegalStateException("Database connection is not available");
        }
        if (sandboxFileService == null) {
            throw new IllegalStateException("Sandbox file service is not available");
        }
    }

    /**
     * List files in a directory with enhanced error handling.
     *
     * @param path Directory path to list files from
     * @return ToolResult containing the list of files
     */
    @Override
    @ToolFunction(description = "List files in a directory")
    public CompletableFuture<ToolResult> listFiles(String path) {
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        
        final String normalizedPath = normalizePath(path);
        logger.info("Listing files in directory (resilient): {}", normalizedPath);
        
        // Check dependencies before proceeding
        checkDependencies();
        
        try {
            // Try normal implementation with proper error handling
            return super.listFiles(path)
                .exceptionally(e -> {
                    logger.error("Error in listFiles: {}", e.getMessage(), e);
                    throw new RuntimeException("Failed to list files: " + e.getMessage(), e);
                });
        } catch (Exception e) {
            logger.error("Exception in listFiles: {}", e.getMessage());
            CompletableFuture<ToolResult> errorFuture = new CompletableFuture<>();
            errorFuture.completeExceptionally(
                new RuntimeException("Failed to list files: " + e.getMessage(), e));
            return errorFuture;
        }
    }
    
    /**
     * Read file content with enhanced error handling.
     *
     * @param path Path to the file to read
     * @return ToolResult containing the file content
     */
    @Override
    @ToolFunction(description = "Read file content")
    public CompletableFuture<ToolResult> readFile(String path) {
        if (path == null || path.isEmpty()) {
            return CompletableFuture.completedFuture(
                new ToolResult(false, "Path parameter is required"));
        }
        
        final String normalizedPath = normalizePath(path);
        logger.info("Reading file (resilient): {}", normalizedPath);
        
        // Check dependencies before proceeding
        checkDependencies();
        
        try {
            // Try normal implementation with proper error handling
            return super.readFile(path)
                .exceptionally(e -> {
                    logger.error("Error in readFile: {}", e.getMessage(), e);
                    throw new RuntimeException("Failed to read file: " + e.getMessage(), e);
                });
        } catch (Exception e) {
            logger.error("Exception in readFile: {}", e.getMessage());
            CompletableFuture<ToolResult> errorFuture = new CompletableFuture<>();
            errorFuture.completeExceptionally(
                new RuntimeException("Failed to read file: " + e.getMessage(), e));
            return errorFuture;
        }
    }
    
    /**
     * Write content to a file with enhanced error handling.
     *
     * @param path Path where the file should be written
     * @param content Content to write to the file
     * @param append If true, append to existing file instead of overwriting
     * @return ToolResult indicating success or failure
     */
    @Override
    @ToolFunction(description = "Write content to a file")
    public CompletableFuture<ToolResult> writeFile(String path, String content, Boolean append) {
        if (path == null || path.isEmpty()) {
            return CompletableFuture.completedFuture(
                new ToolResult(false, "Path parameter is required"));
        }
        
        final String normalizedPath = normalizePath(path);
        logger.info("Writing to file (resilient): {}", normalizedPath);
        
        // Check dependencies before proceeding
        checkDependencies();
        
        try {
            // Try normal implementation with proper error handling
            return super.writeFile(path, content, append)
                .exceptionally(e -> {
                    logger.error("Error in writeFile: {}", e.getMessage(), e);
                    throw new RuntimeException("Failed to write file: " + e.getMessage(), e);
                });
        } catch (Exception e) {
            logger.error("Exception in writeFile: {}", e.getMessage());
            CompletableFuture<ToolResult> errorFuture = new CompletableFuture<>();
            errorFuture.completeExceptionally(
                new RuntimeException("Failed to write file: " + e.getMessage(), e));
            return errorFuture;
        }
    }
    
    /**
     * Convert a FileInfo object to a Map for serialization.
     *
     * @param fileInfo The FileInfo object to convert
     * @return Map representation of the FileInfo
     */
    private Map<String, Object> fileInfoToMap(FileInfo fileInfo) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", fileInfo.getName());
        map.put("path", fileInfo.getPath());
        map.put("is_dir", fileInfo.isDir());
        map.put("size", fileInfo.getSize());
        if (fileInfo.getModTime() != null) {
            map.put("mod_time", fileInfo.getModTime().toString());
        }
        
        if (fileInfo.getPermissions() != null) {
            map.put("permissions", fileInfo.getPermissions());
        }
        
        return map;
    }
}
