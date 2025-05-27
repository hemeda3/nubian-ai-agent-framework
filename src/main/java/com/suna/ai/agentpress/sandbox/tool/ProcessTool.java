package com.Nubian.ai.agentpress.sandbox.tool;

import com.Nubian.ai.agentpress.annotations.ToolFunction;
import com.Nubian.ai.agentpress.model.ToolResult;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

/**
 * Tool for executing processes in a sandbox.
 */
@Slf4j
public class ProcessTool extends SandboxToolBase {

    /**
     * Initialize the process tool for a specific project.
     *
     * @param projectId The project ID
     */
    public ProcessTool(String projectId) {
        super(projectId);
    }

    /**
     * Execute a command in the sandbox.
     *
     * @param command The command to execute
     * @return ToolResult containing the command output
     */
    @ToolFunction(description = "Execute a command in the sandbox")
    public CompletableFuture<ToolResult> executeCommand(String command) {
        log.info("Executing command in sandbox for project {}: {}", getProjectId(), command);
        
        // Ensure sandbox is available and get its ID
        return ensureSandbox()
            .thenCompose(sandbox -> {
                // Execute the command in the sandbox
                return sandboxService.executeCommand(sandbox.getId(), command, false); // Execute synchronously
            })
            .thenApply(output -> {
                // Return the command output as a successful ToolResult
                return new ToolResult(true, output);
            })
            .exceptionally(e -> {
                // Handle errors during command execution
                log.error("Error executing command '{}' in sandbox for project {}: {}", 
                          command, getProjectId(), e.getMessage(), e);
                return new ToolResult(false, "Failed to execute command: " + e.getMessage());
            });
    }

    // TODO: Add other process-related tool functions as needed.
}
