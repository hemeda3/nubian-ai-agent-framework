package com.nubian.ai.agentpress.sandbox.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.nubian.ai.agentpress.sandbox.model.DaytonaWorkspace;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service for managing sandbox lifecycle (start, stop, delete, etc.).
 */
@Service
public class SandboxLifecycleService {
    private static final Logger logger = LoggerFactory.getLogger(SandboxLifecycleService.class);
    
    private final WorkspaceService workspaceService;
    private final SandboxSessionService sessionService;
    
    @Autowired
    public SandboxLifecycleService(
            WorkspaceService workspaceService,
            SandboxSessionService sessionService) {
        this.workspaceService = workspaceService;
        this.sessionService = sessionService;
        
        logger.info("SandboxLifecycleService initialized");
    }
    
    /**
     * Get a sandbox by ID or start it if it's not running.
     *
     * @param sandboxId The sandbox ID
     * @param organizationId Optional organization ID
     * @param sandboxPass The password for the sandbox (if applicable)
     * @return CompletableFuture containing the sandbox
     */
    public CompletableFuture<DaytonaWorkspace> getOrStartSandbox(String sandboxId, String organizationId, String sandboxPass) {
        logger.info("Getting or starting sandbox with ID: {}", sandboxId);
        
        return workspaceService.getWorkspaceDetails(sandboxId, organizationId)
            .thenCompose(workspace -> {
                if ("archived".equalsIgnoreCase(workspace.getState()) || 
                    "stopped".equalsIgnoreCase(workspace.getState())) {
                    
                    logger.info("Sandbox {} is in {} state. Starting...", sandboxId, workspace.getState());
                    // Pass sandboxPass to startWorkspace if needed for authentication
                    return workspaceService.startWorkspace(sandboxId, organizationId) // Assuming startWorkspace doesn't need sandboxPass directly
                        .thenCompose(v -> {
                            // Give the workspace a moment to start
                            try {
                                Thread.sleep(2000);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            return workspaceService.getWorkspaceDetails(sandboxId, organizationId);
                        })
                        .thenCompose(updatedWorkspace -> 
                            sessionService.startSupervisordSession(updatedWorkspace, organizationId) // Assuming startSupervisordSession doesn't need sandboxPass directly
                                .thenApply(v -> updatedWorkspace)
                        );
                }
                
                logger.info("Sandbox {} is ready (state: {})", sandboxId, workspace.getState());
                return CompletableFuture.completedFuture(workspace);
            })
            .exceptionally(e -> {
                logger.error("Error in getOrStartSandbox for ID {}: {}", sandboxId, e.getMessage(), e);
                throw new RuntimeException("Failed to get or start sandbox: " + e.getMessage(), e);
            });
    }
    
    /**
     * Delete a sandbox.
     *
     * @param sandboxId The sandbox ID
     * @param organizationId Optional organization ID
     * @return CompletableFuture that completes when the sandbox is deleted
     */
    public CompletableFuture<Void> deleteSandbox(String sandboxId, String organizationId) {
        logger.info("Deleting sandbox with ID: {}", sandboxId);
        
        return workspaceService.deleteWorkspace(sandboxId, true, organizationId)
            .exceptionally(e -> {
                logger.error("Error deleting sandbox {}: {}", sandboxId, e.getMessage(), e);
                throw new RuntimeException("Failed to delete sandbox: " + e.getMessage(), e);
            });
    }
    
    /**
     * Archive a sandbox.
     *
     * @param sandboxId The sandbox ID
     * @param organizationId Optional organization ID
     * @return CompletableFuture that completes when the sandbox is archived
     */
    public CompletableFuture<Void> archiveSandbox(String sandboxId, String organizationId) {
        logger.info("Archiving sandbox with ID: {}", sandboxId);
        
        return workspaceService.getWorkspaceDetails(sandboxId, organizationId)
            .thenCompose(workspace -> {
                if ("stopped".equalsIgnoreCase(workspace.getState())) {
                    return workspaceService.archiveWorkspace(sandboxId, organizationId);
                } else {
                    logger.info("Skipping sandbox {} as it is not in stopped state (current: {})", 
                              sandboxId, workspace.getState());
                    return CompletableFuture.completedFuture(null);
                }
            })
            .exceptionally(e -> {
                logger.error("Error archiving sandbox {}: {}", sandboxId, e.getMessage(), e);
                throw new RuntimeException("Failed to archive sandbox: " + e.getMessage(), e);
            });
    }
    
    /**
     * Get information about a sandbox.
     *
     * @param sandboxId The sandbox ID
     * @param organizationId Optional organization ID
     * @return CompletableFuture containing information about the sandbox
     */
    public CompletableFuture<Map<String, Object>> getSandboxInfo(String sandboxId, String organizationId) {
        logger.info("Getting information for sandbox with ID: {}", sandboxId);
        
        return workspaceService.getWorkspaceDetails(sandboxId, organizationId)
            .thenApply(workspace -> {
                Map<String, Object> info = new HashMap<>();
                info.put("id", workspace.getId());
                info.put("state", workspace.getState());
                info.put("created_at", workspace.getCreatedAt());
                info.put("name", workspace.getName());
                info.put("image", workspace.getImage());
                info.put("is_public", workspace.isPublic());
                
                if (workspace.getResources() != null) {
                    Map<String, Object> resources = new HashMap<>();
                    resources.put("cpu", workspace.getResources().getCpu());
                    resources.put("memory", workspace.getResources().getMemory());
                    resources.put("disk", workspace.getResources().getDisk());
                    info.put("resources", resources);
                }
                
                if (workspace.getLabels() != null) {
                    info.put("labels", workspace.getLabels());
                }
                
                return info;
            });
    }
}
