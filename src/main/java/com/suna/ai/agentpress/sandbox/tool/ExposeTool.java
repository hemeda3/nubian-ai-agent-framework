package com.Nubian.ai.agentpress.sandbox.tool;

import com.Nubian.ai.agentpress.annotations.ToolFunction;
import com.Nubian.ai.agentpress.model.ToolResult;
import com.Nubian.ai.agentpress.model.ToolExecutionContext;
import com.Nubian.ai.agentpress.model.OpenApiSchema;
import com.Nubian.ai.agentpress.sandbox.service.SandboxService;
import com.Nubian.ai.agentpress.sandbox.service.WorkspaceService; // Added import
import com.Nubian.ai.agentpress.service.ThreadManager;
import com.Nubian.ai.agentpress.service.ContextManager;
import com.Nubian.ai.agentpress.service.DBConnection;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
public class ExposeTool extends SandboxToolBase {

    @Autowired
    public ExposeTool(String projectId) {
        super(projectId);
    }

    @ToolFunction(name = "expose-port", description = "Exposes a port from the sandbox to the public internet.")
    @OpenApiSchema(value = """
        {
          "type": "object",
          "properties": {
            "port": {
              "type": "integer",
              "description": "The port number to expose."
            },
            "description": {
              "type": "string",
              "description": "A description of the service running on the exposed port."
            }
          },
          "required": ["port"]
        }
        """)
    public CompletableFuture<ToolResult> exposePort(ToolExecutionContext context, Integer port, String description) {
        log.info("ExposeTool: Exposing port: {} with description: {}", port, description);
        
        // Get the sandbox ID from the current project
        String sandboxId = getProjectId(); // Assuming projectId is the sandboxId for this context
        
        return workspaceService.getPortPreviewUrl(sandboxId, port, "") // Use workspaceService
                .thenApply(previewUrl -> {
                    String message = String.format("Port %d exposed successfully. Access URL: %s", port, previewUrl);
                    log.info(message);
                    return ToolResult.builder()
                        .success(true)
                        .output(message)
                        .build();
                })
                .exceptionally(e -> {
                    log.error("Error exposing port {}: {}", port, e.getMessage(), e);
                    return ToolResult.builder()
                        .success(false)
                        .output("Failed to expose port " + port + ": " + e.getMessage())
                        .build();
                });
    }

    // Setters for dependencies, used by AgentRunnerService
    public void setSandboxService(SandboxService sandboxService) {
        this.sandboxService = sandboxService;
    }

    public void setThreadManager(ThreadManager threadManager) {
        this.threadManager = threadManager;
    }

    public void setContextManager(ContextManager contextManager) {
        this.contextManager = contextManager;
    }

    public void setDbConnection(DBConnection dbConnection) {
        this.dbConnection = dbConnection;
    }
    
    public void setWorkspaceService(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }
}
