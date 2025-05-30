package com.nubian.ai.agentpress.service;

/**
 * Interface for storage operations.
 * Implementations can use different storage providers (S3, local filesystem, etc).
 */
public interface StorageService {
    
    /**
     * Upload a file to storage.
     *
     * @param bucketName The bucket or container name
     * @param filename The filename to use in storage
     * @param data The file data as byte array
     * @param contentType The MIME content type
     * @return The public URL of the uploaded file
     */
    String uploadFile(String bucketName, String filename, byte[] data, String contentType);
    
    /**
     * Delete a file from storage.
     *
     * @param bucketName The bucket or container name
     * @param filename The filename in storage
     * @return true if successful, false otherwise
     */
    boolean deleteFile(String bucketName, String filename);
    
    /**
     * Get a public URL for a file.
     *
     * @param bucketName The bucket or container name
     * @param filename The filename in storage
     * @return The public URL
     */
    String getPublicUrl(String bucketName, String filename);
}
