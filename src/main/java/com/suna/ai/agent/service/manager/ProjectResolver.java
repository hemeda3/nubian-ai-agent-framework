package com.Nubian.ai.agent.service.manager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.Nubian.ai.postgrest.PostgrestResponse;

import com.Nubian.ai.agentpress.model.Account;
import com.Nubian.ai.agentpress.model.Project;
import com.Nubian.ai.agentpress.service.AccountService;
import com.Nubian.ai.agentpress.service.DBConnection;
import com.Nubian.ai.agentpress.service.ProjectService;
import com.Nubian.ai.agentpress.service.ThreadManager;

import lombok.extern.slf4j.Slf4j;

/**
 * Resolves project IDs for agent runs.
 */
@Slf4j
public class ProjectResolver {

    private final DBConnection dbConnection;
    private final AccountService accountService;
    private final ProjectService projectService;
    private final ThreadManager threadManager;

    /**
     * Initialize ProjectResolver.
     *
     * @param dbConnection The database connection
     * @param accountService The account service
     * @param projectService The project service
     * @param threadManager The thread manager
     */
    public ProjectResolver(
            DBConnection dbConnection,
            AccountService accountService,
            ProjectService projectService,
            ThreadManager threadManager) {
        this.dbConnection = dbConnection;
        this.accountService = accountService;
        this.projectService = projectService;
        this.threadManager = threadManager;
    }

    /**
     * Resolve the project ID for a given thread ID.
     * 
     * @param threadId The thread ID
     * @return The project ID, or a newly created project ID if not found
     */
    public String resolveProjectId(String threadId) {
        try {
            // Query the database to get the project ID using Supabase builder pattern
            CompletableFuture<List<Map<String, Object>>> response = dbConnection.queryForList("threads", "thread_id", threadId); // MODIFIED
                
            List<Map<String, Object>> result = response.join(); // MODIFIED
            Map<String, Object> threadData = null;
            
            if (result != null && !result.isEmpty()) { // MODIFIED
                threadData = result.get(0); // MODIFIED
                
                if (threadData != null && threadData.containsKey("project_id") && threadData.get("project_id") != null) {
                    String projectId = (String) threadData.get("project_id");
                    log.info("Resolved project ID '{}' for thread '{}'", projectId, threadId);
                    return projectId;
                }
            } else {
                // If thread not found, create it with proper Supabase-style table methods
                log.warn("Thread '{}' not found in database, creating it now", threadId);
                
                // Use demo user ID
                String demoUserId = "demo-user-id";
                
                try {
                    // Create a new account directly using table API
                    String accountId = UUID.randomUUID().toString();
                    Map<String, Object> accountData = new HashMap<>();
                    accountData.put("id", accountId);
                    accountData.put("name", "Demo User Account");
                    accountData.put("created_at", java.time.Instant.now().toString());
                    accountData.put("updated_at", java.time.Instant.now().toString());
                    
                    try {
                        Map<String, Object> accountResult = dbConnection.insert("accounts", accountData, false).join(); // MODIFIED
                        
                        log.info("Created new account directly: {}", accountId);
                    } catch (Exception accountEx) {
                        log.error("Error creating account: {}", accountEx.getMessage());
                    }
                    
                    // Create a new project
                    String projectId = UUID.randomUUID().toString();
                    Map<String, Object> projectData = new HashMap<>();
                    projectData.put("project_id", projectId);
                    projectData.put("name", "Demo Project " + projectId.substring(0, 8));
                    projectData.put("account_id", accountId);
                    projectData.put("created_at", java.time.Instant.now().toString());
                    projectData.put("updated_at", java.time.Instant.now().toString());
                    
                    try {
                        Map<String, Object> projectResult = dbConnection.insert("projects", projectData, false).join(); // MODIFIED
                        
                        log.info("Created new project directly: {}", projectId);
                    } catch (Exception projectEx) {
                        log.error("Error creating project: {}", projectEx.getMessage());
                    }
                    
                    // Create the thread with the project and account IDs
                    Map<String, Object> newThreadData = new HashMap<>();
                    newThreadData.put("thread_id", threadId);
                    newThreadData.put("project_id", projectId);
                    newThreadData.put("account_id", accountId);
                    newThreadData.put("created_at", java.time.Instant.now().toString());
                    newThreadData.put("updated_at", java.time.Instant.now().toString());
                    
                    try {
                        Map<String, Object> threadResult = dbConnection.insert("threads", newThreadData, false).join(); // MODIFIED
                        
                        log.info("Created thread '{}' with project ID '{}' and account ID '{}'", 
                                threadId, projectId, accountId);
                    } catch (Exception threadEx) {
                        log.error("Error creating thread: {}", threadEx.getMessage());
                    }
                    
                    return projectId;
                    
                } catch (Exception e) {
                    log.error("Error creating entities directly: {}", e.getMessage(), e);
                    
                    // Fall back to using services if direct creation fails
                    try {
                        // Fallback - try using the services
                        Account account = accountService.getOrCreateAccountForUser(demoUserId).join();
                        Project project = projectService.getOrCreateProjectForAccount(account.getId(), demoUserId).join();
                        
                        // Create the thread using the thread manager
                        threadManager.createThread(project.getId(), account.getId());
                        
                        log.info("Created entities using services as fallback");
                        return project.getId();
                    } catch (Exception fallbackEx) {
                        log.error("Error in fallback creation: {}", fallbackEx.getMessage(), fallbackEx);
                        throw new RuntimeException("Error in fallback creation: " + fallbackEx.getMessage(), fallbackEx);
                    }
                }
            }
            
            // If no project ID was found or created, throw an exception
            log.error("Could not resolve or create project ID for thread '{}'", threadId);
            throw new RuntimeException("Failed to resolve or create project ID for thread: " + threadId);
        } catch (Exception e) {
            log.error("Error resolving project ID: {}", e.getMessage(), e);
            throw new RuntimeException("Error resolving project ID: " + e.getMessage(), e);
        }
    }
}
