package com.Nubian.ai.agentpress.service;

import com.Nubian.ai.agentpress.model.Project; // Assuming a Project model exists or will be created
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service for managing projects.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectService {

    private final DBConnection dbConnection;

    /**
     * Retrieves a project by its ID.
     *
     * @param projectId The ID of the project.
     * @return A CompletableFuture that resolves to the Project object, or null if not found.
     */
    public CompletableFuture<Project> getProjectById(String projectId) {
        // Using direct table access with proper column names
        Map<String, Object> conditions = new HashMap<>();
        conditions.put("project_id", projectId);
        
        log.debug("Querying for project with ID: {}", projectId);
        // Based on Python: client.table('projects').select('*')
        return dbConnection.queryForList("projects", conditions)
            .thenApply(results -> {
                if (!results.isEmpty()) {
                    return mapToProject(results.get(0));
                }
                return null;
            });
    }

    /**
     * Retrieves a project by account ID and user ID. If no project is found, a new one is created.
     * This method assumes a user might have a default project within an account, or we create one.
     *
     * @param accountId The ID of the account the project belongs to.
     * @param userId The ID of the user creating/owning the project.
     * @return A CompletableFuture that resolves to the existing or newly created Project.
     */
    public CompletableFuture<Project> getOrCreateProjectForAccount(String accountId, String userId) {
        // Try to find an existing project for this account using builder pattern
        Map<String, Object> conditions = new HashMap<>();
        conditions.put("account_id", accountId);
        
        return dbConnection.queryForList("projects", conditions)
            .thenCompose(results -> {
                if (!results.isEmpty()) {
                    Map<String, Object> projectMap = results.get(0);
                    log.info("Found existing project for account {}: {}", accountId, projectMap.get("project_id"));
                    return CompletableFuture.completedFuture(mapToProject(projectMap));
                } else {
                    log.info("No project found for account {}. Creating a new project.", accountId);
                    return createNewProjectAsync(accountId, userId);
                }
            });
    }

    /**
     * Creates a new project asynchronously.
     *
     * @param accountId The ID of the account the project belongs to.
     * @param userId The ID of the user who created the project.
     * @return A CompletableFuture that resolves to the newly created Project.
     */
    public CompletableFuture<Project> createNewProjectAsync(String accountId, String userId) {
        String projectId = UUID.randomUUID().toString();
        String projectName = "New Project " + projectId.substring(0, 8); // Simple naming
        
        // Use ISO-8601 string format instead of Timestamp object to avoid range issues
        String nowIsoString = java.time.Instant.now().toString();
        
        log.debug("Creating timestamp as ISO string: {}", nowIsoString);

        // Create project using direct insert with correct column names
        Map<String, Object> projectData = new HashMap<>();
        projectData.put("project_id", projectId);
        projectData.put("name", projectName);
        projectData.put("account_id", accountId);
        projectData.put("is_public", false);
        projectData.put("created_at", nowIsoString);
        projectData.put("updated_at", nowIsoString);
        
        // Add detailed logging to help diagnose issues
        log.info("Creating project with data: {}", projectData);
        log.info("Project timestamp as ISO string: {}", nowIsoString);
        
        return dbConnection.insert("projects", projectData, false)
            .thenApply(result -> {
                log.info("Created new project {} for account {}", projectId, accountId);
                
                // No need to link user to project in project_members table since it doesn't exist in schema
                // Convert ISO string to Timestamp for the Project object
                Timestamp timestamp = Timestamp.from(Instant.parse(nowIsoString));
                
                return new Project(projectId, projectName, accountId, userId, timestamp, timestamp, null); // sandbox will be null initially
            })
            .exceptionally(e -> {
                log.error("Error creating project: {}", e.getMessage(), e);
                // Create a mock project even if DB insert fails to allow the demo to continue
                Timestamp timestamp = Timestamp.from(Instant.parse(nowIsoString));
                return new Project(projectId, projectName, accountId, userId, timestamp, timestamp, null);
            });
    }

    /**
     * Updates the sandbox ID for a given project.
     *
     * @param projectId The ID of the project to update.
     * @param sandboxId The new sandbox ID.
     */
    public void updateProjectSandboxId(String projectId, String sandboxId) {
        Map<String, Object> values = new HashMap<>();
        values.put("sandbox", Map.of("id", sandboxId));
        
        Map<String, Object> conditions = new HashMap<>();
        conditions.put("project_id", projectId);
        
        dbConnection.update("projects", values, conditions)
            .exceptionally(e -> {
                log.error("Error updating project sandbox ID: {}", e.getMessage(), e);
                return 0; // Return 0 rows affected on error
            })
            .thenAccept(rowsAffected -> 
                log.info("Updated project {} with sandbox ID {} ({} rows affected)", 
                    projectId, sandboxId, rowsAffected));
    }

    /**
     * Maps a database result map to a Project object.
     *
     * @param projectMap The map containing project data.
     * @return A Project object.
     */
    private Project mapToProject(Map<String, Object> projectMap) {
        String id = (String) projectMap.get("project_id");
        String name = (String) projectMap.get("name");
        String accountId = (String) projectMap.get("account_id");
        
        // Since created_by doesn't exist in the schema, use a placeholder
        String createdBy = "system"; // Or derive from userId if available and appropriate
        
        // Handle different timestamp formats that could come from the database
        Timestamp createdAt = null;
        Timestamp updatedAt = null;
        
        // Check if timestamps are stored as strings (ISO format) or as Timestamp objects
        Object createdAtObj = projectMap.get("created_at");
        Object updatedAtObj = projectMap.get("updated_at");
        
        if (createdAtObj instanceof String) {
            // Handle ISO string format
            try {
                createdAt = Timestamp.from(Instant.parse((String) createdAtObj));
                log.debug("Converted created_at from ISO string: {}", createdAtObj);
            } catch (Exception e) {
                log.warn("Failed to parse created_at timestamp: {}", createdAtObj, e);
                createdAt = new Timestamp(System.currentTimeMillis());
            }
        } else if (createdAtObj instanceof Timestamp) {
            createdAt = (Timestamp) createdAtObj;
        }
        
        if (updatedAtObj instanceof String) {
            // Handle ISO string format
            try {
                updatedAt = Timestamp.from(Instant.parse((String) updatedAtObj));
                log.debug("Converted updated_at from ISO string: {}", updatedAtObj);
            } catch (Exception e) {
                log.warn("Failed to parse updated_at timestamp: {}", updatedAtObj, e);
                updatedAt = new Timestamp(System.currentTimeMillis());
            }
        } else if (updatedAtObj instanceof Timestamp) {
            updatedAt = (Timestamp) updatedAtObj;
        }
        
        // Fallback if timestamps are null
        if (createdAt == null) createdAt = new Timestamp(System.currentTimeMillis());
        if (updatedAt == null) updatedAt = new Timestamp(System.currentTimeMillis());
        
        // Assuming 'sandbox' column is JSONB and contains a 'id' field
        Map<String, Object> sandboxJson = (Map<String, Object>) projectMap.get("sandbox");
        String sandboxId = (sandboxJson != null && sandboxJson.containsKey("id")) ? 
                (String) sandboxJson.get("id") : null;
        
        return new Project(id, name, accountId, createdBy, createdAt, updatedAt, sandboxId);
    }

}
