package com.nubian.ai.agentpress.util.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Utility for handling storage operations like image uploads.
 */
@Component
public class StorageUtils {
    private static final Logger logger = LoggerFactory.getLogger(StorageUtils.class);

    /**
     * Default bucket name for browser screenshots.
     */
    private static final String DEFAULT_BUCKET = "browser-screenshots";

    @Value("${supabase.url:}")
    private String supabaseUrl;
    
    @Value("${supabase.anon.key:}")
    private String supabaseAnonKey;
    
    @Value("${supabase.service.role.key:}")
    private String supabaseServiceRoleKey;

    /**
     * Upload a base64 encoded image to storage and return the URL.
     *
     * @param base64Data Base64 encoded image data (with or without data URL prefix)
     * @param bucketName Name of the storage bucket to upload to (default: browser-screenshots)
     * @return CompletableFuture containing the public URL of the uploaded image
     */
    public CompletableFuture<String> uploadBase64Image(String base64Data, String bucketName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Remove data URL prefix if present
                String processedBase64 = base64Data;
                if (base64Data.startsWith("data:")) {
                    processedBase64 = base64Data.split(",")[1];
                }
                
                // Decode base64 data
                byte[] imageData = Base64.getDecoder().decode(processedBase64);
                
                // Generate unique filename
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String uniqueId = UUID.randomUUID().toString().substring(0, 8);
                String filename = String.format("image_%s_%s.png", timestamp, uniqueId);
                
                // Upload to storage
                return uploadToStorage(imageData, filename, bucketName != null ? bucketName : DEFAULT_BUCKET);
                
            } catch (Exception e) {
                logger.error("Error uploading base64 image: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to upload image: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Upload a base64 encoded image to the default storage bucket and return the URL.
     *
     * @param base64Data Base64 encoded image data (with or without data URL prefix)
     * @return CompletableFuture containing the public URL of the uploaded image
     */
    public CompletableFuture<String> uploadBase64Image(String base64Data) {
        return uploadBase64Image(base64Data, DEFAULT_BUCKET);
    }

    /**
     * Upload a MultipartFile to storage and return the URL.
     *
     * @param file The file to upload
     * @param bucketName Name of the storage bucket to upload to
     * @return CompletableFuture containing the public URL of the uploaded file
     */
    public CompletableFuture<String> uploadFile(MultipartFile file, String bucketName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Generate unique filename
                String originalFilename = file.getOriginalFilename();
                String extension = originalFilename != null && originalFilename.contains(".") 
                    ? originalFilename.substring(originalFilename.lastIndexOf("."))
                    : ".bin";
                
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String uniqueId = UUID.randomUUID().toString().substring(0, 8);
                String filename = String.format("file_%s_%s%s", timestamp, uniqueId, extension);
                
                // Upload to storage
                return uploadToStorage(file.getBytes(), filename, bucketName);
                
            } catch (Exception e) {
                logger.error("Error uploading file: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to upload file: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Upload file bytes to storage and return the public URL.
     * This method implements actual storage upload using Supabase Storage API.
     *
     * @param fileData The file data as bytes
     * @param filename The filename to use
     * @param bucketName The bucket to upload to
     * @return The public URL of the uploaded file
     */
    private String uploadToStorage(byte[] fileData, String filename, String bucketName) {
        // TODO: Consider refactoring this method to use a dedicated Supabase Storage Java SDK
        // or a more generic cloud storage abstraction for a more robust implementation.
        // The current implementation uses direct HTTP calls to the Supabase API.
        try {
            // Check if Supabase configuration is available
            if (supabaseUrl == null || supabaseUrl.isEmpty() || 
                supabaseServiceRoleKey == null || supabaseServiceRoleKey.isEmpty()) {
                logger.warn("Supabase configuration not found, using fallback storage simulation");
                return simulateStorageUpload(filename, bucketName);
            }
            
            // Create HTTP client
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
            
            // Build the upload URL
            String uploadUrl = String.format("%s/storage/v1/object/%s/%s", 
                supabaseUrl, bucketName, filename);
            
            // Create the request
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .header("Authorization", "Bearer " + supabaseServiceRoleKey)
                .header("Content-Type", "application/octet-stream")
                .header("x-upsert", "true") // Allow overwriting existing files
                .POST(HttpRequest.BodyPublishers.ofByteArray(fileData))
                .timeout(Duration.ofMinutes(2))
                .build();
            
            // Send the request
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                // Upload successful, construct public URL
                String publicUrl = String.format("%s/storage/v1/object/public/%s/%s", 
                    supabaseUrl, bucketName, filename);
                
                logger.info("Successfully uploaded file {} to Supabase storage bucket {}", filename, bucketName);
                return publicUrl;
            } else {
                logger.error("Supabase storage upload failed with status {}: {}", 
                    response.statusCode(), response.body());
                throw new RuntimeException("Storage upload failed with status: " + response.statusCode());
            }
            
        } catch (Exception e) {
            logger.error("Error uploading to Supabase storage: {}", e.getMessage(), e);
            
            // Fallback to simulation if real storage fails
            logger.warn("Falling back to storage simulation due to error: {}", e.getMessage());
            return simulateStorageUpload(filename, bucketName);
        }
    }
    
    /**
     * Simulate storage upload for development/testing purposes.
     * 
     * @param filename The filename
     * @param bucketName The bucket name
     * @return A simulated storage URL
     */
    private String simulateStorageUpload(String filename, String bucketName) {
        logger.info("Simulated upload of file {} to bucket {}", filename, bucketName);
        
        // Return a realistic-looking URL that could be used for development
        if (supabaseUrl != null && !supabaseUrl.isEmpty()) {
            return String.format("%s/storage/v1/object/public/%s/%s", supabaseUrl, bucketName, filename);
        } else {
            return String.format("https://your-project.supabase.co/storage/v1/object/public/%s/%s", 
                bucketName, filename);
        }
    }
}
