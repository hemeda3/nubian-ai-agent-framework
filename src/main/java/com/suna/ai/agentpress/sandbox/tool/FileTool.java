package com.Nubian.ai.agentpress.sandbox.tool;

import com.Nubian.ai.agentpress.annotations.ToolFunction;
import com.Nubian.ai.agentpress.model.ToolResult;
import com.Nubian.ai.agentpress.sandbox.model.FileInfo;


import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Tool for interacting with files in a sandbox.
 * Provides operations for listing, reading, writing, and deleting files.
 */
public class FileTool extends SandboxToolBase {
    
    /**
     * Initialize the file tool for a specific project.
     *
     * @param projectId The project ID
     */
    public FileTool(String projectId) {
        super(projectId);
    }
    
    /**
     * List files in a directory.
     *
     * @param path Directory path to list files from
     * @return ToolResult containing the list of files
     */
    @ToolFunction(description = "List files in a directory")
    public CompletableFuture<ToolResult> listFiles(String path) {
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        
        final String normalizedPath = normalizePath(path);
        logger.info("Listing files in directory: {}", normalizedPath);
        
        return ensureSandbox()
            .thenCompose(sandbox -> sandboxFileService.listFiles(sandbox.getId(), normalizedPath))
            .thenApply(files -> {
                List<Map<String, Object>> fileList = files.stream()
                    .map(this::fileInfoToMap)
                    .collect(Collectors.toList());
                
                Map<String, Object> result = new HashMap<>();
                result.put("files", fileList);
                result.put("directory", normalizedPath);
                result.put("count", fileList.size());
                
                return new ToolResult(true, result);
            })
            .exceptionally(e -> {
                logger.error("Error listing files in {}: {}", normalizedPath, e.getMessage(), e);
                return new ToolResult(false, "Failed to list files: " + e.getMessage());
            });
    }
    
    /**
     * Read file content.
     *
     * @param path Path to the file to read
     * @return ToolResult containing the file content
     */
    @ToolFunction(description = "Read file content")
    public CompletableFuture<ToolResult> readFile(String path) {
        if (path == null || path.isEmpty()) {
            return CompletableFuture.completedFuture(
                new ToolResult(false, "Path parameter is required"));
        }
        
        final String normalizedPath = normalizePath(path);
        logger.info("Reading file: {}", normalizedPath);
        
        return ensureSandbox()
            .thenCompose(sandbox -> sandboxFileService.downloadFile(sandbox.getId(), normalizedPath))
            .thenApply(content -> {
                String fileContent = new String(content, StandardCharsets.UTF_8);
                
                Map<String, Object> result = new HashMap<>();
                result.put("content", fileContent);
                result.put("path", normalizedPath);
                
                return new ToolResult(true, result);
            })
            .exceptionally(e -> {
                logger.error("Error reading file {}: {}", normalizedPath, e.getMessage(), e);
                return new ToolResult(false, "Failed to read file: " + e.getMessage());
            });
    }
    
    /**
     * Write content to a file.
     *
     * @param path Path where the file should be written
     * @param content Content to write to the file
     * @param append If true, append to existing file instead of overwriting
     * @return ToolResult indicating success or failure
     */
    @ToolFunction(description = "Write content to a file")
    public CompletableFuture<ToolResult> writeFile(String path, String content, Boolean append) {
        if (path == null || path.isEmpty()) {
            return CompletableFuture.completedFuture(
                new ToolResult(false, "Path parameter is required"));
        }
        
        if (content == null) {
            content = "";
        }
        
        if (append == null) {
            append = false;
        }
        
        final String normalizedPath = normalizePath(path);
        final String finalContent = content;
        final boolean finalAppend = append;
        
        logger.info("Writing to file: {} (append: {})", normalizedPath, finalAppend);
        
        return ensureSandbox()
            .thenCompose(sandbox -> {
                byte[] contentBytes = finalContent.getBytes(StandardCharsets.UTF_8);
                
                if (finalAppend) {
                    // In append mode, first read the existing content, then append the new content
                    return sandboxFileService.downloadFile(sandbox.getId(), normalizedPath)
                        .thenCompose(existingContent -> {
                            String existingText = new String(existingContent, StandardCharsets.UTF_8);
                            String combinedText = existingText + finalContent;
                            byte[] combinedBytes = combinedText.getBytes(StandardCharsets.UTF_8);
                            return sandboxFileService.uploadFile(sandbox.getId(), normalizedPath, combinedBytes);
                        })
                        .exceptionally(e -> {
                            // If file doesn't exist, just write the new content
                            return sandboxFileService.uploadFile(sandbox.getId(), normalizedPath, contentBytes)
                                .join();
                        });
                } else {
                    return sandboxFileService.uploadFile(sandbox.getId(), normalizedPath, contentBytes);
                }
            })
            .thenApply(v -> {
                Map<String, Object> result = new HashMap<>();
                result.put("path", normalizedPath);
                result.put("bytes_written", finalContent.getBytes(StandardCharsets.UTF_8).length);
                result.put("append", finalAppend);
                
                return new ToolResult(true, result);
            })
            .exceptionally(e -> {
                logger.error("Error writing to file {}: {}", normalizedPath, e.getMessage(), e);
                return new ToolResult(false, "Failed to write to file: " + e.getMessage());
            });
    }
    
    /**
     * Create a directory.
     *
     * @param path Path where to create the directory
     * @return ToolResult indicating success or failure
     */
    @ToolFunction(description = "Create a directory")
    public CompletableFuture<ToolResult> createDirectory(String path) {
        if (path == null || path.isEmpty()) {
            return CompletableFuture.completedFuture(
                new ToolResult(false, "Path parameter is required"));
        }
        
        final String normalizedPath = normalizePath(path);
        logger.info("Creating directory: {}", normalizedPath);
        
        return ensureSandbox()
            .thenCompose(sandbox -> sandboxFileService.createDirectory(sandbox.getId(), normalizedPath, "755"))
            .thenApply(v -> {
                Map<String, Object> result = new HashMap<>();
                result.put("path", normalizedPath);
                result.put("created", true);
                
                return new ToolResult(true, result);
            })
            .exceptionally(e -> {
                logger.error("Error creating directory {}: {}", normalizedPath, e.getMessage(), e);
                return new ToolResult(false, "Failed to create directory: " + e.getMessage());
            });
    }
    
    /**
     * Delete a file or directory.
     *
     * @param path Path to the file or directory to delete
     * @param recursive If true, recursively delete directories
     * @return ToolResult indicating success or failure
     */
    @ToolFunction(description = "Delete a file or directory")
    public CompletableFuture<ToolResult> delete(String path, Boolean recursive) {
        if (path == null || path.isEmpty()) {
            return CompletableFuture.completedFuture(
                new ToolResult(false, "Path parameter is required"));
        }
        
        if (recursive == null) {
            recursive = false;
        }
        
        final String normalizedPath = normalizePath(path);
        final boolean finalRecursive = recursive;
        
        logger.info("Deleting path: {} (recursive: {})", normalizedPath, finalRecursive);
        
        return ensureSandbox()
            .thenCompose(sandbox -> sandboxFileService.delete(sandbox.getId(), normalizedPath, finalRecursive))
            .thenApply(v -> {
                Map<String, Object> result = new HashMap<>();
                result.put("path", normalizedPath);
                result.put("deleted", true);
                result.put("recursive", finalRecursive);
                
                return new ToolResult(true, result);
            })
            .exceptionally(e -> {
                logger.error("Error deleting {}: {}", normalizedPath, e.getMessage(), e);
                return new ToolResult(false, "Failed to delete path: " + e.getMessage());
            });
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
        map.put("mod_time", fileInfo.getModTime().toString());
        
        if (fileInfo.getPermissions() != null) {
            map.put("permissions", fileInfo.getPermissions());
        }
        
        return map;
    }
}
