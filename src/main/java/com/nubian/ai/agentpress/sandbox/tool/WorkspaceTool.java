package com.nubian.ai.agentpress.sandbox.tool;

import com.nubian.ai.agentpress.annotations.ToolFunction;
import com.nubian.ai.agentpress.model.ToolResult;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

/**
 * Tool for managing sandbox workspaces.
 */
@Slf4j
public class WorkspaceTool extends SandboxToolBase {

    /**
     * Initialize the workspace tool for a specific project.
     *
     * @param projectId The project ID
     */
    public WorkspaceTool(String projectId) {
        super(projectId);
    }

    /**
     * List available workspaces.
     *
     * @return ToolResult containing the list of workspaces
     */
    @ToolFunction(description = "List available workspaces")
    public CompletableFuture<ToolResult> listWorkspaces() {
        log.info("Listing workspaces for project {}", getProjectId());
        return sandboxService.listWorkspaces(getProjectId()) // Use the correct method from SandboxService
                .thenApply(workspaces -> new ToolResult(true, workspaces))
                .exceptionally(e -> {
                    log.error("Error listing workspaces: {}", e.getMessage(), e);
                    return new ToolResult(false, "Failed to list workspaces: " + e.getMessage());
                });
    }

    /**
     * Create a new workspace.
     *
     * @param workspaceName The name of the workspace to create.
     * @return ToolResult indicating success or failure.
     */
    @ToolFunction(description = "Create a new workspace")
    public CompletableFuture<ToolResult> createWorkspace(String workspaceName) {
        log.info("Creating workspace '{}' for project {}", workspaceName, getProjectId());
        // Assuming createSandbox can be used to create a new workspace within a project context
        // Note: The current createSandbox takes password and organizationId which might not be directly applicable here.
        // This might need a more specific sandboxService.createWorkspace method.
        // For now, we'll simulate or adapt.
        return sandboxService.createSandbox("default-password", getProjectId(), "default-org") // Simplified
                .thenApply(workspace -> new ToolResult(true, "Workspace '" + workspace.getName() + "' created with ID: " + workspace.getId()))
                .exceptionally(e -> {
                    log.error("Error creating workspace: {}", e.getMessage(), e);
                    return new ToolResult(false, "Failed to create workspace: " + e.getMessage());
                });
    }

    /**
     * Delete a workspace.
     *
     * @param workspaceId The ID of the workspace to delete.
     * @return ToolResult indicating success or failure.
     */
    @ToolFunction(description = "Delete a workspace")
    public CompletableFuture<ToolResult> deleteWorkspace(String workspaceId) {
        log.info("Deleting workspace '{}' for project {}", workspaceId, getProjectId());
        return sandboxService.deleteSandbox(workspaceId)
                .thenApply(v -> new ToolResult(true, "Workspace '" + workspaceId + "' deleted successfully"))
                .exceptionally(e -> {
                    log.error("Error deleting workspace: {}", e.getMessage(), e);
                    return new ToolResult(false, "Failed to delete workspace: " + e.getMessage());
                });
    }
}
