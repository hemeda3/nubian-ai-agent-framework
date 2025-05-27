package com.Nubian.ai.agentpress.service.impl;

import com.Nubian.ai.agentpress.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Local filesystem implementation of StorageService.
 * Stores files on the local filesystem and serves them via a base URL.
 */
@Service
public class LocalStorageService implements StorageService {
    private static final Logger logger = LoggerFactory.getLogger(LocalStorageService.class);
    
    @Value("${storage.base.path:./storage}")
    private String baseStoragePath;
    
    @Value("${storage.base.url:http://localhost:8080/storage}")
    private String baseStorageUrl;
    
    @Override
    public String uploadFile(String bucketName, String filename, byte[] data, String contentType) {
        try {
            // Create directory structure if it doesn't exist
            Path bucketPath = Paths.get(baseStoragePath, bucketName);
            Files.createDirectories(bucketPath);
            
            // Write file
            Path filePath = bucketPath.resolve(filename);
            try (FileOutputStream outputStream = new FileOutputStream(filePath.toFile())) {
                outputStream.write(data);
            }
            
            logger.info("File uploaded to local storage: {}", filePath);
            return getPublicUrl(bucketName, filename);
        } catch (IOException e) {
            logger.error("Error uploading file to local storage: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to upload file to local storage", e);
        }
    }
    
    @Override
    public boolean deleteFile(String bucketName, String filename) {
        Path filePath = Paths.get(baseStoragePath, bucketName, filename);
        try {
            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                logger.info("File deleted from local storage: {}", filePath);
            } else {
                logger.warn("File not found for deletion: {}", filePath);
            }
            return deleted;
        } catch (IOException e) {
            logger.error("Error deleting file from local storage: {}", e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public String getPublicUrl(String bucketName, String filename) {
        // Ensure URL has correct format with forward slashes
        String url = baseStorageUrl;
        if (!url.endsWith("/")) {
            url += "/";
        }
        return url + bucketName + "/" + filename;
    }
    
    /**
     * Initialize the storage directories if they don't exist.
     */
    public void initialize() {
        try {
            // Create base storage directory
            Path basePath = Paths.get(baseStoragePath);
            Files.createDirectories(basePath);
            logger.info("Local storage initialized at: {}", basePath.toAbsolutePath());
        } catch (IOException e) {
            logger.error("Error initializing local storage: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initialize local storage", e);
        }
    }
}
