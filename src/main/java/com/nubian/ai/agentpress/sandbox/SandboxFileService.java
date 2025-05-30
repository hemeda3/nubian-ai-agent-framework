package com.nubian.ai.agentpress.sandbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.nubian.ai.agentpress.sandbox.model.FileInfo;
import com.nubian.ai.agentpress.sandbox.service.FileService;
import com.nubian.ai.agentpress.sandbox.service.SandboxService;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service for managing files within sandboxes.
 * This is a wrapper around the FileService that ensures a sandbox is available
 * before performing file operations.
 */
@Service
public class SandboxFileService {
    private static final Logger logger = LoggerFactory.getLogger(SandboxFileService.class);
    
    private final SandboxService sandboxService;
    private final FileService fileService;
    
    @Autowired
    public SandboxFileService(SandboxService sandboxService, FileService fileService) {
        this.sandboxService = sandboxService;
        this.fileService = fileService;
    }
    
    /**
     * Ensure a sandbox is running before performing file operations.
     *
     * @param sandboxId The sandbox ID
     * @return CompletableFuture containing the sandbox ID when it's ready
     */
    private CompletableFuture<String> ensureSandbox(String sandboxId) {
        return sandboxService.getOrStartSandbox(sandboxId)
            .thenApply(workspace -> workspace.getId());
    }
    
    /**
     * List files in a directory.
     *
     * @param sandboxId The sandbox ID
     * @param path The directory path
     * @return CompletableFuture containing the list of files
     */
    public CompletableFuture<List<FileInfo>> listFiles(String sandboxId, String path) {
        final String normalizedPath = normalizePath(path);
        return ensureSandbox(sandboxId)
            .thenCompose(id -> fileService.listFiles(id, normalizedPath, null));
    }
    
    /**
     * Delete a file.
     *
     * @param sandboxId The sandbox ID
     * @param path The file path
     * @param recursive Whether to delete recursively
     * @return CompletableFuture that completes when the file is deleted
     */
    public CompletableFuture<Void> delete(String sandboxId, String path, boolean recursive) {
        final String normalizedPath = normalizePath(path);
        return ensureSandbox(sandboxId)
            .thenCompose(id -> fileService.deleteFile(id, normalizedPath, recursive, null));
    }
    
    /**
     * Download a file.
     *
     * @param sandboxId The sandbox ID
     * @param path The file path
     * @return CompletableFuture containing the file content
     */
    public CompletableFuture<byte[]> downloadFile(String sandboxId, String path) {
        final String normalizedPath = normalizePath(path);
        return ensureSandbox(sandboxId)
            .thenCompose(id -> fileService.downloadFile(id, normalizedPath, null));
    }
    
    /**
     * Upload a file.
     *
     * @param sandboxId The sandbox ID
     * @param path The file path
     * @param content The file content
     * @return CompletableFuture that completes when the file is uploaded
     */
    public CompletableFuture<Void> uploadFile(String sandboxId, String path, byte[] content) {
        final String normalizedPath = normalizePath(path);
        return ensureSandbox(sandboxId)
            .thenCompose(id -> fileService.uploadFile(id, normalizedPath, content, null));
    }
    
    /**
     * Create a directory.
     *
     * @param sandboxId The sandbox ID
     * @param path The directory path
     * @param mode The directory mode (e.g., "755")
     * @return CompletableFuture that completes when the directory is created
     */
    public CompletableFuture<Void> createDirectory(String sandboxId, String path, String mode) {
        final String normalizedPath = normalizePath(path);
        return ensureSandbox(sandboxId)
            .thenCompose(id -> fileService.createDirectory(id, normalizedPath, mode, null));
    }
    
    /**
     * Get file information.
     *
     * @param sandboxId The sandbox ID
     * @param path The file path
     * @return CompletableFuture containing the file information
     */
    public CompletableFuture<FileInfo> getFileInfo(String sandboxId, String path) {
        final String normalizedPath = normalizePath(path);
        return ensureSandbox(sandboxId)
            .thenCompose(id -> fileService.getFileInfo(id, normalizedPath, null));
    }
    
    /**
     * Normalize a path to ensure proper UTF-8 encoding and handling.
     *
     * @param path The file path, potentially containing URL-encoded characters
     * @return Normalized path with proper UTF-8 encoding
     */
    public String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        
        // Decode any URL-encoded characters
        try {
            path = URLDecoder.decode(path, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            logger.warn("Error decoding path {}: {}", path, e.getMessage());
            // Continue with the original path if decoding fails
        }
        
        // Ensure path starts with /
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        
        // Remove any trailing slashes (except for root path)
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        
        return path;
    }
}
