package com.nubian.ai.agentpress.sandbox.tool;

import com.nubian.ai.agentpress.annotations.ToolFunction;
import com.nubian.ai.agentpress.model.ToolResult;
import com.nubian.ai.agentpress.model.ToolExecutionContext;
import com.nubian.ai.agentpress.model.OpenApiSchema;
import com.nubian.ai.agentpress.sandbox.service.SandboxService;
import com.nubian.ai.agentpress.service.ThreadManager;
import com.nubian.ai.agentpress.service.ContextManager;
import com.nubian.ai.agentpress.service.DBConnection;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
public class DeployTool extends SandboxToolBase {

    @Autowired
    public DeployTool(String projectId) {
        super(projectId);
    }

    @ToolFunction(name = "deploy-project", description = "Deploys the current project to a specified environment.")
    @OpenApiSchema(value = """
        {
          "type": "object",
          "properties": {
            "environment": {
              "type": "string",
              "description": "The environment to deploy to (e.g., 'staging', 'production').",
              "enum": ["staging", "production", "development"]
            },
            "buildCommand": {
              "type": "string",
              "description": "Optional build command to run before deployment."
            }
          },
          "required": ["environment"]
        }
        """)
    public CompletableFuture<ToolResult> deployProject(ToolExecutionContext context, String environment, String buildCommand) {
        log.info("DeployTool: Deploying project to environment: {}", environment);
        // Check if the real implementation is available
        if (sandboxService == null) {
            throw new IllegalStateException("Sandbox service not available for deployment");
        }
        
        // For now, this is not implemented yet, so throw an exception
        throw new UnsupportedOperationException("Deployment functionality is not yet implemented");
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
}
