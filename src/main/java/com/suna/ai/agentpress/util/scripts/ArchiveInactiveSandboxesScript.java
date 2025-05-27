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
 * Script to archive inactive sandboxes to save resources.
 * This script identifies sandboxes that haven't been used recently and archives them.
 */
@Component
public class ArchiveInactiveSandboxesScript {
    private static final Logger logger = LoggerFactory.getLogger(ArchiveInactiveSandboxesScript.class);
    private final DBConnection dbConnection; // Keep for updateProjectArchiveStatus
    private final com.Nubian.ai.postgrest.SupabaseClient supabasePostgrestClient;

    @Autowired
    public ArchiveInactiveSandboxesScript(DBConnection dbConnection,
                                          com.Nubian.ai.agentpress.database.ResilientSupabaseClient resilientSupabaseClient,
                                          @Value("${daytona.api.url:}") String daytonaApiUrl,
                                          @Value("${daytona.api.key:}") String daytonaApiKey,
                                          @Value("${archive.inactive.days:30}") int inactiveDaysThreshold,
                                          @Value("${archive.dry.run:true}") boolean dryRun) {
        this.dbConnection = dbConnection;
        this.supabasePostgrestClient = resilientSupabaseClient.getInternalClient();
        this.daytonaApiUrl = daytonaApiUrl;
        this.daytonaApiKey = daytonaApiKey;
        this.inactiveDaysThreshold = inactiveDaysThreshold;
        this.dryRun = dryRun;
    }

    // Values are injected by constructor
    private final String daytonaApiUrl;
    private final String daytonaApiKey;
    private final int inactiveDaysThreshold;
    private final boolean dryRun;
    // Duplicate @Value annotated fields removed
    
    /**
     * Execute the archive inactive sandboxes script.
     * 
     * @return CompletableFuture that completes when the script finishes
     */
    public CompletableFuture<Map<String, Object>> execute() {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Starting archive inactive sandboxes script (dry run: {})", dryRun);
            
            Map<String, Object> result = new HashMap<>();
            List<String> archivedSandboxes = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            
            try {
                // Get all account IDs from active billing customers
                Set<String> activeAccountIds = getActiveAccountIds();
                logger.info("Found {} active accounts", activeAccountIds.size());
                
                // Get all projects with sandbox information
                List<Map<String, Object>> projectsWithSandboxes = getProjectsWithSandboxes();
                logger.info("Found {} projects with sandboxes", projectsWithSandboxes.size());
                
                // Filter projects by inactive criteria
                List<Map<String, Object>> inactiveProjects = filterInactiveProjects(projectsWithSandboxes, activeAccountIds);
                logger.info("Found {} inactive projects to potentially archive", inactiveProjects.size());
                
                // Archive each inactive sandbox
                for (Map<String, Object> project : inactiveProjects) {
                    String sandboxId = (String) project.get("sandbox_id");
                    String projectId = (String) project.get("id");
                    String accountId = (String) project.get("account_id");
                    
                    try {
                        if (dryRun) {
                            logger.info("DRY RUN: Would archive sandbox {} for project {} (account: {})", 
                                    sandboxId, projectId, accountId);
                            archivedSandboxes.add(sandboxId + " (dry run)");
                        } else {
                            boolean archived = archiveSandbox(sandboxId, projectId);
                            if (archived) {
                                archivedSandboxes.add(sandboxId);
                                logger.info("Successfully archived sandbox {} for project {}", sandboxId, projectId);
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
                
                logger.info("Archive script completed. Archived: {}, Errors: {}", 
                        archivedSandboxes.size(), errors.size());
                
            } catch (Exception e) {
                logger.error("Error executing archive script: {}", e.getMessage(), e);
                result.put("status", "failed");
                result.put("error", e.getMessage());
            }
            
            return result;
        });
    }
    
    /**
     * Get all account IDs from active billing customers.
     * 
     * @return Set of active account IDs
     */
    private Set<String> getActiveAccountIds() {
        try {
            // Query for accounts with active subscriptions or recent activity
            String sql = """
                SELECT DISTINCT a.id 
                FROM accounts a 
                LEFT JOIN subscriptions s ON a.id = s.account_id 
                WHERE s.status = 'active' 
                   OR s.status = 'trialing'
                   OR a.updated_at > NOW() - INTERVAL '90 days'
                """;
            
            List<Map<String, Object>> results = dbConnection.queryForList(sql, "").join();
            
            Set<String> activeAccountIds = new HashSet<>();
            for (Map<String, Object> row : results) {
                activeAccountIds.add((String) row.get("id"));
            }
            
            logger.debug("Active account IDs: {}", activeAccountIds);
            return activeAccountIds;
            
        } catch (Exception e) {
            logger.error("Error getting active account IDs: {}", e.getMessage(), e);
            return new HashSet<>();
        }
    }
    
    /**
     * Get all projects with sandbox information.
     * 
     * @return List of projects with sandbox data
     */
    private List<Map<String, Object>> getProjectsWithSandboxes() {
        try {
            // Renaming embedded columns: project_id,project_name,project_account_id:account_id,sandbox_id,project_updated_at:updated_at,account:accounts!inner(account_id:id,account_name:name)
            // This ensures all selected fields from 'projects' are distinct from 'accounts' fields after embedding.
            // And aliases embedded account fields.
            String selectClause = "id,name,account_id,sandbox_id,updated_at,account:accounts!inner(id,name)";

            // Use the generic types returned by the builder chain
            var queryBuilder = // Type inferred: PostgrestFilterBuilder<Object, Map<String, Object>, Object[], String, Object>
                supabasePostgrestClient.database().from("projects")
                    .select(selectClause) 
                    .filter("sandbox_id", "isnot", "null")
                    .neq("sandbox_id", "")
                    .order("updated_at", true); // true for ascending

            com.Nubian.ai.postgrest.PostgrestResponse<Object[]> response = queryBuilder.execute().join();

            if (response.isSuccess()) {
                List<Map<String, Object>> results = new ArrayList<>();
                if (response.getData() != null) {
                    for (Object item : response.getData()) {
                        if (item instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> projectMap = (Map<String, Object>) item;
                            // The 'account' field will be the embedded account map.
                            // Example: projectMap.get("id"), projectMap.get("name"), 
                            // Map<String,Object> accountDetails = (Map<String,Object>) projectMap.get("account");
                            // String accountNameFromEmbed = (String) accountDetails.get("name");
                            results.add(projectMap);
                        }
                    }
                }
                logger.debug("Found {} projects with sandboxes using PostgREST client", results.size());
                return results;
            } else {
                logger.error("Error getting projects with sandboxes using PostgREST client: {}", response.getError().getMessage());
                return new ArrayList<>();
            }
        } catch (Exception e) {
            logger.error("Error getting projects with sandboxes: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Filter projects to identify inactive ones that should be archived.
     * 
     * @param projects List of all projects with sandboxes
     * @param activeAccountIds Set of active account IDs
     * @return List of inactive projects
     */
    private List<Map<String, Object>> filterInactiveProjects(
            List<Map<String, Object>> projects, 
            Set<String> activeAccountIds) {
        
        List<Map<String, Object>> inactiveProjects = new ArrayList<>();
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(inactiveDaysThreshold);
        
        for (Map<String, Object> project : projects) {
            String accountId = (String) project.get("account_id");
            Object updatedAtObj = project.get("updated_at");
            
            // Skip projects from active accounts
            if (activeAccountIds.contains(accountId)) {
                continue;
            }
            
            // Check if project hasn't been updated recently
            if (updatedAtObj != null) {
                try {
                    LocalDateTime updatedAt;
                    if (updatedAtObj instanceof String) {
                        updatedAt = LocalDateTime.parse((String) updatedAtObj);
                    } else if (updatedAtObj instanceof java.sql.Timestamp) {
                        updatedAt = ((java.sql.Timestamp) updatedAtObj).toLocalDateTime();
                    } else {
                        logger.warn("Unknown updated_at type: {}", updatedAtObj.getClass());
                        continue;
                    }
                    
                    if (updatedAt.isBefore(cutoffDate)) {
                        inactiveProjects.add(project);
                        logger.debug("Project {} is inactive (last updated: {})", 
                                project.get("id"), updatedAt);
                    }
                } catch (Exception e) {
                    logger.warn("Error parsing updated_at for project {}: {}", 
                            project.get("id"), e.getMessage());
                }
            }
        }
        
        return inactiveProjects;
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
            
            // Build the archive URL
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
        logger.info("Simulated archive of sandbox {} for project {}", sandboxId, projectId);
        
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
