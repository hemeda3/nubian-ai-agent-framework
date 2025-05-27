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
 * Script to archive old sandboxes that haven't been used recently.
 * This script identifies sandboxes based on age and usage patterns.
 */
@Component
public class ArchiveOldSandboxesScript {
    private static final Logger logger = LoggerFactory.getLogger(ArchiveOldSandboxesScript.class);
    private final DBConnection dbConnection; // Keep for updateProjectArchiveStatus if it's simple
    private final com.Nubian.ai.postgrest.SupabaseClient supabasePostgrestClient; // Inject SupabaseClient

    @Autowired
    public ArchiveOldSandboxesScript(DBConnection dbConnection, 
                                     com.Nubian.ai.agentpress.database.ResilientSupabaseClient resilientSupabaseClient,
                                     @Value("${daytona.api.url:}") String daytonaApiUrl,
                                     @Value("${daytona.api.key:}") String daytonaApiKey,
                                     @Value("${archive.old.days:60}") int oldDaysThreshold,
                                     @Value("${archive.dry.run:true}") boolean dryRun) {
        this.dbConnection = dbConnection;
        this.supabasePostgrestClient = resilientSupabaseClient.getInternalClient();
        this.daytonaApiUrl = daytonaApiUrl;
        this.daytonaApiKey = daytonaApiKey;
        this.oldDaysThreshold = oldDaysThreshold;
        this.dryRun = dryRun;
    }
    
    // Values are injected by constructor
    private final String daytonaApiUrl;
    private final String daytonaApiKey;
    private final int oldDaysThreshold;
    private final boolean dryRun;
    
    /**
     * Execute the archive old sandboxes script.
     * 
     * @return CompletableFuture that completes when the script finishes
     */
    public CompletableFuture<Map<String, Object>> execute() {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Starting archive old sandboxes script (dry run: {})", dryRun);
            
            Map<String, Object> result = new HashMap<>();
            List<String> archivedSandboxes = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            
            try {
                // Get projects older than the threshold with sandboxes
                List<Map<String, Object>> oldProjects = getProjectsOlderThanThreshold();
                logger.info("Found {} old projects to potentially archive", oldProjects.size());
                
                // Archive each old sandbox
                for (Map<String, Object> project : oldProjects) {
                    String sandboxId = (String) project.get("sandbox_id");
                    String projectId = (String) project.get("id");
                    String projectName = (String) project.get("name");
                    Object createdAtObj = project.get("created_at");
                    
                    try {
                        if (dryRun) {
                            logger.info("DRY RUN: Would archive old sandbox {} for project {} ({})", 
                                    sandboxId, projectId, projectName);
                            archivedSandboxes.add(sandboxId + " (dry run)");
                        } else {
                            boolean archived = archiveSandbox(sandboxId, projectId);
                            if (archived) {
                                archivedSandboxes.add(sandboxId);
                                logger.info("Successfully archived old sandbox {} for project {}", sandboxId, projectId);
                            } else {
                                errors.add("Failed to archive sandbox " + sandboxId);
                            }
                        }
                    } catch (Exception e) {
                        String error = "Error archiving sandbox " + sandboxId + ": " + e.getMessage();
                        errors.add(error);
                        logger.error(error, e);
                    }
                }
                
                result.put("status", "completed");
                result.put("archived_count", archivedSandboxes.size());
                result.put("archived_sandboxes", archivedSandboxes);
                result.put("error_count", errors.size());
                result.put("errors", errors);
                result.put("dry_run", dryRun);
                result.put("execution_time", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                
                logger.info("Archive old sandboxes script completed. Archived: {}, Errors: {}", 
                        archivedSandboxes.size(), errors.size());
                
            } catch (Exception e) {
                logger.error("Error executing archive old sandboxes script: {}", e.getMessage(), e);
                result.put("status", "failed");
                result.put("error", e.getMessage());
            }
            
            return result;
        });
    }
    
    /**
     * Get projects older than the threshold with sandboxes.
     * 
     * @return List of old projects with sandbox data
     */
    private List<Map<String, Object>> getProjectsOlderThanThreshold() {
        try {
            LocalDateTime cutoffDateTime = LocalDateTime.now().minusDays(oldDaysThreshold);
            String cutoffDateString = cutoffDateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

            // Select projects and embed account information
            // Adjust select columns as needed, e.g., "id,name,account_id,sandbox_id,created_at,updated_at,accounts!inner(id,name)"
            // The original SQL selected a.id as account_id, which might conflict if p.account_id is also selected.
            // Assuming we want p.account_id and then account details from the joined 'accounts' table.
            // Using "projects?select=*,accounts!inner(*)" to get all columns from both, ensuring join.
            // Use var for type inference to resolve generic type mismatches from the builder chain
            var queryBuilder = // Type inferred: PostgrestFilterBuilder<Object, Map<String, Object>, Object[], String, Object>
                supabasePostgrestClient.database().from("projects")
                    .select("*,accounts!inner(id,name)") // !inner for JOIN behavior
                    .filter("sandbox_id", "isnot", "null")
                    .neq("sandbox_id", "")
                    .lt("created_at", cutoffDateString)
                    .or("archived.is.null,archived.eq.false", null) 
                    .order("created_at", true); // MODIFIED: Use the simpler order(column, ascending)

            com.Nubian.ai.postgrest.PostgrestResponse<Object[]> response = queryBuilder.execute().join();

            if (response.isSuccess()) {
                List<Map<String, Object>> results = new ArrayList<>();
                if (response.getData() != null) {
                    for (Object item : response.getData()) {
                        if (item instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> projectMap = (Map<String, Object>) item;
                            // The 'accounts' field will be the embedded account map or list
                            results.add(projectMap);
                        }
                    }
                }
                logger.debug("Found {} old projects with sandboxes using PostgREST client", results.size());
                return results;
            } else {
                logger.error("Error getting old projects with PostgREST client: {}", response.getError().getMessage());
                return new ArrayList<>();
            }
        } catch (Exception e) {
            logger.error("Error getting old projects with sandboxes: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Archive a single sandbox using the Daytona API.
     * 
     * @param sandboxId The sandbox ID to archive
     * @param projectId The project ID for logging
     * @return true if successfully archived, false otherwise
     */
    private boolean archiveSandbox(String sandboxId, String projectId) {
        try {
            // Check if Daytona API configuration is available
            if (daytonaApiUrl == null || daytonaApiUrl.isEmpty() || 
                daytonaApiKey == null || daytonaApiKey.isEmpty()) {
                logger.warn("Daytona API configuration not found, simulating archive for sandbox {}", sandboxId);
                return simulateArchive(sandboxId, projectId);
            }
            
            // Create HTTP client
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
            
            // Build the archive URL - using stop endpoint to archive the sandbox
            String archiveUrl = String.format("%s/workspace/%s/stop", daytonaApiUrl, sandboxId);
            
            // Create the request
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(archiveUrl))
                .header("Authorization", "Bearer " + daytonaApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .timeout(Duration.ofMinutes(2))
                .build();
            
            // Send the request
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                logger.info("Successfully archived sandbox {} via Daytona API", sandboxId);
                
                // Update the project status in database
                updateProjectArchiveStatus(projectId, true);
                
                return true;
            } else {
                logger.error("Daytona API archive failed for sandbox {} with status {}: {}", 
                        sandboxId, response.statusCode(), response.body());
                return false;
            }
            
        } catch (Exception e) {
            logger.error("Error archiving sandbox {} via Daytona API: {}", sandboxId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Simulate archiving a sandbox for development/testing purposes.
     * 
     * @param sandboxId The sandbox ID
     * @param projectId The project ID
     * @return true (always succeeds in simulation)
     */
    private boolean simulateArchive(String sandboxId, String projectId) {
        logger.info("Simulated archive of old sandbox {} for project {}", sandboxId, projectId);
        
        // Update the project status in database
        updateProjectArchiveStatus(projectId, true);
        
        return true;
    }
    
    /**
     * Update the project's archive status in the database.
     * 
     * @param projectId The project ID
     * @param archived Whether the project is archived
     */
    private void updateProjectArchiveStatus(String projectId, boolean archived) {
        try {
            Map<String, Object> values = new HashMap<>();
            values.put("archived", archived);
            values.put("archived_at", archived ? Timestamp.valueOf(LocalDateTime.now()) : null);

            Map<String, Object> conditions = new HashMap<>();
            conditions.put("id", projectId);
            
            dbConnection.update("projects", values, conditions).join(); // Using join to ensure completion for logging
            logger.debug("Updated archive status for project {} to {}", projectId, archived);
            
        } catch (Exception e) {
            logger.error("Error updating archive status for project {}: {}", projectId, e.getMessage(), e);
        }
    }
}
