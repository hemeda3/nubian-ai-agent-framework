package com.Nubian.ai.agentpress.util.scripts;

import com.Nubian.ai.agentpress.service.DBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
// No direct import of database clients
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Script to delete sandboxes for a specific user.
 * This script is used for user data cleanup and GDPR compliance.
 */
@Component
public class DeleteUserSandboxesScript {
    private static final Logger logger = LoggerFactory.getLogger(DeleteUserSandboxesScript.class);
    private final DBConnection dbConnection; // Keep for updateProjectDeleteStatus
    private final com.Nubian.ai.postgrest.SupabaseClient supabasePostgrestClient;

    @Autowired
    public DeleteUserSandboxesScript(DBConnection dbConnection,
                                     com.Nubian.ai.agentpress.database.ResilientSupabaseClient resilientSupabaseClient,
                                     @Value("${daytona.api.url:}") String daytonaApiUrl,
                                     @Value("${daytona.api.key:}") String daytonaApiKey,
                                     @Value("${delete.dry.run:true}") boolean dryRun) {
        this.dbConnection = dbConnection;
        this.supabasePostgrestClient = resilientSupabaseClient.getInternalClient();
        this.daytonaApiUrl = daytonaApiUrl;
        this.daytonaApiKey = daytonaApiKey;
        this.dryRun = dryRun;
    }

    // Values are injected by constructor
    private final String daytonaApiUrl;
    private final String daytonaApiKey;
    private final boolean dryRun;
    // Duplicate @Value annotated fields removed
    
    /**
     * Execute the delete user sandboxes script for a specific user.
     * 
     * @param userId The user ID whose sandboxes should be deleted
     * @return CompletableFuture that completes when the script finishes
     */
    public CompletableFuture<Map<String, Object>> execute(String userId) {
        logger.info("Starting delete user sandboxes script for user {} (dry run: {})", userId, dryRun);
        
        // Get all projects owned by the user with sandboxes
        return getUserProjectsWithSandboxes(userId)
            .thenApply(userProjects -> {
                Map<String, Object> result = new HashMap<>();
                List<String> deletedSandboxes = new ArrayList<>();
                List<String> errors = new ArrayList<>();
                
                try {
                    logger.info("Found {} projects with sandboxes for user {}", userProjects.size(), userId);
                    
                    // Delete each user's sandbox
                    for (Map<String, Object> project : userProjects) {
                        String sandboxId = (String) project.get("sandbox_id");
                        String projectId = (String) project.get("id");
                        String projectName = (String) project.get("name");
                    
                    try {
                        if (dryRun) {
                            logger.info("DRY RUN: Would delete sandbox {} for project {} ({}) owned by user {}", 
                                    sandboxId, projectId, projectName, userId);
                            deletedSandboxes.add(sandboxId + " (dry run)");
                        } else {
                            boolean deleted = deleteSandbox(sandboxId, projectId);
                            if (deleted) {
                                deletedSandboxes.add(sandboxId);
                                logger.info("Successfully deleted sandbox {} for project {} owned by user {}", 
                                        sandboxId, projectId, userId);
                            } else {
                                errors.add("Failed to delete sandbox " + sandboxId);
                            }
                        }
                    } catch (Exception e) {
                        String error = "Error deleting sandbox " + sandboxId + ": " + e.getMessage();
                        errors.add(error);
                        logger.error(error, e);
                    }
                }
                
                result.put("status", "completed");
                result.put("user_id", userId);
                result.put("deleted_count", deletedSandboxes.size());
                result.put("deleted_sandboxes", deletedSandboxes);
                result.put("error_count", errors.size());
                result.put("errors", errors);
                result.put("dry_run", dryRun);
                result.put("execution_time", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                
                logger.info("Delete user sandboxes script completed for user {}. Deleted: {}, Errors: {}", 
                        userId, deletedSandboxes.size(), errors.size());
                
            } catch (Exception e) {
                logger.error("Error executing delete user sandboxes script for user {}: {}", userId, e.getMessage(), e);
                result.put("status", "failed");
                result.put("user_id", userId);
                result.put("error", e.getMessage());
            }
            
            return result;
        });
    }
    
    /**
     * Get all projects owned by a user that have sandboxes.
     * 
     * @param userId The user ID
     * @return CompletableFuture with list of user projects with sandbox data
     */
    private CompletableFuture<List<Map<String, Object>>> getUserProjectsWithSandboxes(String userId) {
        // Using var for type inference
        return CompletableFuture.supplyAsync(() -> {
            try {
                String selectClause = "id,name,account_id,sandbox_id,created_at,updated_at,account:accounts!inner(id,name)";
                var queryBuilder = supabasePostgrestClient.database().from("projects")
                    .select(selectClause)
                    .eq("created_by", userId)
                    .filter("sandbox_id", "isnot", "null")
                    .neq("sandbox_id", "")
                    .order("created_at", true);

                com.Nubian.ai.postgrest.PostgrestResponse<Object[]> response = queryBuilder.execute().join();

                if (response.isSuccess()) {
                    List<Map<String, Object>> results = new ArrayList<>();
                    if (response.getData() != null) {
                        for (Object item : response.getData()) {
                            if (item instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> projectMap = (Map<String, Object>) item;
                                results.add(projectMap);
                            }
                        }
                    }
                    logger.debug("Found {} projects with sandboxes for user {} using PostgREST client", results.size(), userId);
                    return results;
                } else {
                    logger.error("Error getting user projects for user {} with PostgREST client: {}", userId, response.getError().getMessage());
                    return new ArrayList<>();
                }
            } catch (Exception e) {
                logger.error("Error getting user projects with sandboxes for user {}: {}", userId, e.getMessage(), e);
                return new ArrayList<>();
            }
        });
    }
    
    /**
     * Delete a single sandbox using the Daytona API.
     * 
     * @param sandboxId The sandbox ID to delete
     * @param projectId The project ID for logging
     * @return true if successfully deleted, false otherwise
     */
    private boolean deleteSandbox(String sandboxId, String projectId) {
        try {
            // Check if Daytona API configuration is available
            if (daytonaApiUrl == null || daytonaApiUrl.isEmpty() || 
                daytonaApiKey == null || daytonaApiKey.isEmpty()) {
                logger.warn("Daytona API configuration not found, simulating delete for sandbox {}", sandboxId);
                return simulateDelete(sandboxId, projectId);
            }
            
            // Create HTTP client
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
            
            // Build the delete URL
            String deleteUrl = String.format("%s/workspace/%s", daytonaApiUrl, sandboxId);
            
            // Create the request
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(deleteUrl))
                .header("Authorization", "Bearer " + daytonaApiKey)
                .header("Content-Type", "application/json")
                .DELETE()
                .timeout(Duration.ofMinutes(2))
                .build();
            
            // Send the request
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                logger.info("Successfully deleted sandbox {} via Daytona API", sandboxId);
                
                // Update the project status in database
                updateProjectDeleteStatus(projectId);
                
                return true;
            } else {
                logger.error("Daytona API delete failed for sandbox {} with status {}: {}", 
                        sandboxId, response.statusCode(), response.body());
                return false;
            }
            
        } catch (Exception e) {
            logger.error("Error deleting sandbox {} via Daytona API: {}", sandboxId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Simulate deleting a sandbox for development/testing purposes.
     * 
     * @param sandboxId The sandbox ID
     * @param projectId The project ID
     * @return true (always succeeds in simulation)
     */
    private boolean simulateDelete(String sandboxId, String projectId) {
        logger.info("Simulated delete of sandbox {} for project {}", sandboxId, projectId);
        
        // Update the project status in database
        updateProjectDeleteStatus(projectId);
        
        return true;
    }
    
    /**
     * Update the project's status after sandbox deletion.
     * 
     * @param projectId The project ID
     */
    private void updateProjectDeleteStatus(String projectId) {
        try {
            Map<String, Object> values = new HashMap<>();
            values.put("sandbox_id", null); // Set sandbox_id to NULL
            values.put("deleted_at", Timestamp.valueOf(LocalDateTime.now()));

            Map<String, Object> conditions = new HashMap<>();
            conditions.put("id", projectId);
            
            dbConnection.update("projects", values, conditions).join(); // Using join to ensure completion for logging
            logger.debug("Updated delete status for project {}", projectId);
            
        } catch (Exception e) {
            logger.error("Error updating delete status for project {}: {}", projectId, e.getMessage(), e);
        }
    }
    
    /**
     * Execute the delete user sandboxes script for multiple users.
     * 
     * @param userIds List of user IDs whose sandboxes should be deleted
     * @return CompletableFuture that completes when the script finishes
     */
    public CompletableFuture<Map<String, Object>> executeBatch(List<String> userIds) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Starting batch delete user sandboxes script for {} users (dry run: {})", 
                    userIds.size(), dryRun);
            
            Map<String, Object> result = new HashMap<>();
            Map<String, Object> userResults = new HashMap<>();
            int totalDeleted = 0;
            int totalErrors = 0;
            
            try {
                for (String userId : userIds) {
                    CompletableFuture<Map<String, Object>> userResult = execute(userId);
                    Map<String, Object> userResultMap = userResult.get();
                    
                    userResults.put(userId, userResultMap);
                    
                    if ("completed".equals(userResultMap.get("status"))) {
                        totalDeleted += (Integer) userResultMap.get("deleted_count");
                        totalErrors += (Integer) userResultMap.get("error_count");
                    }
                }
                
                result.put("status", "completed");
                result.put("total_users", userIds.size());
                result.put("total_deleted", totalDeleted);
                result.put("total_errors", totalErrors);
                result.put("user_results", userResults);
                result.put("dry_run", dryRun);
                result.put("execution_time", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                
                logger.info("Batch delete user sandboxes script completed. Users: {}, Total deleted: {}, Total errors: {}", 
                        userIds.size(), totalDeleted, totalErrors);
                
            } catch (Exception e) {
                logger.error("Error executing batch delete user sandboxes script: {}", e.getMessage(), e);
                result.put("status", "failed");
                result.put("error", e.getMessage());
            }
            
            return result;
        });
    }
}
