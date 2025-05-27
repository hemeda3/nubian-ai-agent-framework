package com.Nubian.ai.agentpress.sandbox.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.core.type.TypeReference; // Added import
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import com.Nubian.ai.agentpress.sandbox.client.HttpClientService;
import com.Nubian.ai.agentpress.sandbox.model.FileInfo;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service for managing files in a sandbox.
 */
@Service
public class FileService {
    private static final Logger logger = LoggerFactory.getLogger(FileService.class);
    
    private final HttpClientService httpClient;
    
    @Autowired
    public FileService(HttpClientService httpClient) {
        this.httpClient = httpClient;
    }
    
    /**
     * List files in a directory.
     *
     * @param workspaceId The workspace ID
     * @param path The directory path
     * @param organizationId Optional organization ID
     * @return CompletableFuture containing the list of files
     */
    public CompletableFuture<List<FileInfo>> listFiles(String workspaceId, String path, String organizationId) {
        logger.info("Listing files in workspace: {} at path: {}", workspaceId, path);
        String apiPath = "/toolbox/" + workspaceId + "/toolbox/files?path=" + path;
        // Use TypeReference for correct deserialization of generic lists
        return httpClient.get(apiPath, new TypeReference<List<FileInfo>>() {}, organizationId);
    }
    
    /**
     * Delete a file or directory.
     *
     * @param workspaceId The workspace ID
     * @param path The file path
     * @param recursive Whether to delete recursively
     * @param organizationId Optional organization ID
     * @return CompletableFuture that completes when the file is deleted
     */
    public CompletableFuture<Void> deleteFile(String workspaceId, String path, boolean recursive, String organizationId) {
        logger.info("Deleting file in workspace: {} at path: {} (recursive: {})", 
                   workspaceId, path, recursive);
        
        String apiPath = "/toolbox/" + workspaceId + "/toolbox/files?path=" + path;
        if (recursive) {
            apiPath += "&recursive=true";
        }
        
        return httpClient.delete(apiPath, Void.class, organizationId);
    }
    
    /**
     * Download a file.
     *
     * @param workspaceId The workspace ID
     * @param path The file path
     * @param organizationId Optional organization ID
     * @return CompletableFuture containing the file content
     */
    public CompletableFuture<byte[]> downloadFile(String workspaceId, String path, String organizationId) {
        logger.info("Downloading file from workspace: {} at path: {}", workspaceId, path);
        String apiPath = "/toolbox/" + workspaceId + "/toolbox/files/download?path=" + path;
        return httpClient.get(apiPath, byte[].class, organizationId);
    }
    
    /**
     * Upload a file.
     *
     * @param workspaceId The workspace ID
     * @param path The file path
     * @param content The file content
     * @param organizationId Optional organization ID
     * @return CompletableFuture that completes when the file is uploaded
     */
    public CompletableFuture<Void> uploadFile(String workspaceId, String path, byte[] content, String organizationId) {
        logger.info("Uploading file to workspace: {} at path: {}", workspaceId, path);
        
        // Note: This implementation is simplified. In a real implementation, we would need to use
        // multipart/form-data or similar to upload files properly.
        String apiPath = "/toolbox/" + workspaceId + "/toolbox/files/upload?path=" + path;
        return httpClient.post(apiPath, content, Void.class, organizationId);
    }
    
    /**
     * Create a directory.
     *
     * @param workspaceId The workspace ID
     * @param path The directory path
     * @param mode The directory mode (e.g., "755")
     * @param organizationId Optional organization ID
     * @return CompletableFuture that completes when the directory is created
     */
    public CompletableFuture<Void> createDirectory(String workspaceId, String path, String mode, String organizationId) {
        logger.info("Creating directory in workspace: {} at path: {} with mode: {}", 
                   workspaceId, path, mode);
        
        String apiPath = "/toolbox/" + workspaceId + "/toolbox/files/folder?path=" + path;
        if (mode != null && !mode.isEmpty()) {
            apiPath += "&mode=" + mode;
        }
        
        return httpClient.post(apiPath, null, Void.class, organizationId);
    }
    
    /**
     * Get file information.
     *
     * @param workspaceId The workspace ID
     * @param path The file path
     * @param organizationId Optional organization ID
     * @return CompletableFuture containing the file information
     */
    public CompletableFuture<FileInfo> getFileInfo(String workspaceId, String path, String organizationId) {
        logger.info("Getting file info in workspace: {} for path: {}", workspaceId, path);
        String apiPath = "/toolbox/" + workspaceId + "/toolbox/files/info?path=" + path;
        return httpClient.get(apiPath, FileInfo.class, organizationId);
    }
}
