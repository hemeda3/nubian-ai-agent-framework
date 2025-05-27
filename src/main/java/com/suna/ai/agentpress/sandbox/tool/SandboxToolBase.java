package com.Nubian.ai.agentpress.sandbox.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.Nubian.ai.agentpress.model.Tool;
import com.Nubian.ai.agentpress.sandbox.SandboxFileService;
import com.Nubian.ai.agentpress.sandbox.model.DaytonaWorkspace;
import com.Nubian.ai.agentpress.sandbox.service.SandboxService;
import com.Nubian.ai.agentpress.service.ContextManager;
import com.Nubian.ai.agentpress.service.ThreadManager;
import com.Nubian.ai.agentpress.util.file.FileUtils;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base class for all sandbox tools that provides project-based sandbox access.
 */
public abstract class SandboxToolBase extends Tool {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    
    private static final String WORKSPACE_PATH = "/workspace";
    
    // Class variable to track if sandbox URLs have been printed
    private static final AtomicBoolean urlsPrinted = new AtomicBoolean(false);
    
    protected SandboxService sandboxService;
    protected SandboxFileService sandboxFileService;
    protected com.Nubian.ai.agentpress.sandbox.service.WorkspaceService workspaceService;
    protected ThreadManager threadManager;
    protected ContextManager contextManager;
    protected com.Nubian.ai.agentpress.service.DBConnection dbConnection;
    
    private final String projectId;
    private DaytonaWorkspace sandbox;
    private String sandboxId;
    private String sandboxPass;
    
    /**
     * Constructor for a sandbox tool.
     *
     * @param projectId Project ID associated with the sandbox
     */
    public SandboxToolBase(String projectId) {
        this.projectId = projectId;
    }
    
    // Setters for dependencies (to be manually injected when not using @Component)
    public void setSandboxService(SandboxService sandboxService) {
        this.sandboxService = sandboxService;
    }
    
    public void setSandboxFileService(SandboxFileService sandboxFileService) {
        this.sandboxFileService = sandboxFileService;
    }

    public void setWorkspaceService(com.Nubian.ai.agentpress.sandbox.service.WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }
    
    public void setThreadManager(ThreadManager threadManager) {
        this.threadManager = threadManager;
    }
    
    public void setContextManager(ContextManager contextManager) {
        this.contextManager = contextManager;
    }
    
    public void setDbConnection(com.Nubian.ai.agentpress.service.DBConnection dbConnection) {
        this.dbConnection = dbConnection;
    }
    
    /**
     * Ensure we have a valid sandbox instance, retrieving it from the project if needed.
     *
     * @return CompletableFuture containing the sandbox object
     */
    protected CompletableFuture<DaytonaWorkspace> ensureSandbox() {
        if (sandbox != null) {
            return CompletableFuture.completedFuture(sandbox);
        }
        
        return getSandboxFromProject()
            .thenCompose(workspace -> {
                this.sandbox = workspace;
                
                // Log URLs if not already printed
                if (urlsPrinted.compareAndSet(false, true)) {
                    // Display actual URLs from DaytonaWorkspace if available
                    if (workspace != null) {
                        String vncUrl = workspace.getVncUrl();
                        String webUrl = workspace.getWebsiteUrl();
                        logger.info("Sandbox {} ready: VNC URL: {}, Website URL: {}", 
                                   sandboxId, vncUrl != null ? vncUrl : "N/A", 
                                   webUrl != null ? webUrl : "N/A");
                    } else {
                        logger.info("Sandbox {} has no workspace information", sandboxId);
                    }
                }
                
                return CompletableFuture.completedFuture(workspace);
            });
    }
    
    /**
     * Check if essential dependencies are available.
     * 
     * @throws IllegalStateException if dependencies are missing
     */
    protected void checkDependencies() {
        if (dbConnection == null) {
            throw new IllegalStateException("Database connection is not available");
        }
        if (sandboxService == null) {
            throw new IllegalStateException("Sandbox service is not available");
        }
    }
    
    /**
     * Get the sandbox object for the project.
     *
     * @return CompletableFuture containing the sandbox object
     */
    private CompletableFuture<DaytonaWorkspace> getSandboxFromProject() {
        // Check dependencies before proceeding
        checkDependencies();
        
        try {
            // Query the database to get the sandbox information from the project's JSONB 'sandbox' column
            String sql = "SELECT sandbox FROM projects WHERE id = ?";
            
            return dbConnection.queryForList(sql, projectId)
                .thenCompose(results -> {
                    // Process query results
                    if (results.isEmpty()) {
                        logger.warn("No project found with ID {}, using default sandbox ID and password", projectId);
                        this.sandboxId = "sandbox_" + projectId;
                        this.sandboxPass = "password"; // Default password
                        return sandboxService.getOrStartSandbox(this.sandboxId, null, this.sandboxPass)
                            .exceptionally(e -> {
                                logger.error("Error starting sandbox after no project found: {}", e.getMessage(), e);
                                throw new RuntimeException("Failed to start sandbox after no project found", e);
                            });
                    } else {
                        Map<String, Object> project = results.get(0);
                        // Assuming 'sandbox' column is JSONB and contains 'id' and 'pass' fields
                        @SuppressWarnings("unchecked")
                        Map<String, Object> sandboxJson = (Map<String, Object>) project.get("sandbox");
                        
                        if (sandboxJson != null) {
                            this.sandboxId = (String) sandboxJson.get("id");
                            this.sandboxPass = (String) sandboxJson.get("pass"); // Assuming 'pass' field exists
                            // Retrieve organization_id if it's project-specific
                            // Assuming organization_id is also stored in the sandbox JSONB
                            String organizationId = (String) sandboxJson.get("organization_id");
                            
                            if (this.sandboxId == null || this.sandboxId.isEmpty()) {
                                logger.warn("Project {} has null or empty sandbox.id in JSONB, using default", projectId);
                                this.sandboxId = "sandbox_" + projectId;
                            }
                            
                            if (this.sandboxPass == null || this.sandboxPass.isEmpty()) {
                                this.sandboxPass = "password"; // Default password
                            }
                            
                            logger.info("Retrieved sandbox ID '{}' for project {}", this.sandboxId, projectId);
                            
                            // Now use the sandboxId, organizationId, and sandboxPass to get or start the sandbox
                            return sandboxService.getOrStartSandbox(this.sandboxId, organizationId, this.sandboxPass)
                                .exceptionally(e -> {
                                    logger.error("Error starting sandbox: {}", e.getMessage(), e);
                                    throw new RuntimeException("Failed to start sandbox", e);
                                });
                        } else {
                            logger.warn("Project {} has null sandbox JSONB, using default sandbox ID and password", projectId);
                            this.sandboxId = "sandbox_" + projectId;
                            this.sandboxPass = "password"; // Default password
                            return sandboxService.getOrStartSandbox(this.sandboxId, null, this.sandboxPass) // Pass null for organizationId if not found
                                .exceptionally(e -> {
                                    logger.error("Error starting sandbox with null JSONB: {}", e.getMessage(), e);
                                    throw new RuntimeException("Failed to start sandbox with null JSONB", e);
                                });
                        }
                    }
                })
                .exceptionally(e -> {
                    logger.error("Database error or sandbox service error retrieving/starting sandbox for project {}: {}", projectId, e.getMessage(), e);
                    throw new RuntimeException("Failed to retrieve sandbox information", e);
                });
        } catch (Exception e) {
            logger.error("Error retrieving sandbox for project {}: {}", projectId, e.getMessage(), e);
            CompletableFuture<DaytonaWorkspace> errorFuture = new CompletableFuture<>();
            errorFuture.completeExceptionally(new RuntimeException("Error retrieving sandbox: " + e.getMessage(), e));
            return errorFuture;
        }
    }
    
    /**
     * Get the sandbox object.
     *
     * @return The sandbox object
     * @throws IllegalStateException if the sandbox is not initialized
     */
    protected DaytonaWorkspace getSandbox() {
        if (sandbox == null) {
            throw new IllegalStateException("Sandbox not initialized. Call ensureSandbox() first.");
        }
        return sandbox;
    }
    
    /**
     * Get the sandbox ID.
     *
     * @return The sandbox ID
     * @throws IllegalStateException if the sandbox ID is not initialized
     */
    protected String getSandboxId() {
        if (sandboxId == null) {
            throw new IllegalStateException("Sandbox ID not initialized. Call ensureSandbox() first.");
        }
        return sandboxId;
    }
    
    /**
     * Get the project ID.
     *
     * @return The project ID
     */
    protected String getProjectId() {
        return projectId;
    }
    
    /**
     * Clean and normalize a path to be relative to the workspace.
     *
     * @param path The path to clean
     * @return The cleaned path
     */
    protected String cleanPath(String path) {
        return FileUtils.cleanPath(path, WORKSPACE_PATH);
    }
    
    /**
     * Normalize a path to ensure proper UTF-8 encoding and handling.
     *
     * @param path The file path, potentially containing URL-encoded characters
     * @return Normalized path with proper UTF-8 encoding
     */
    protected String normalizePath(String path) {
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
