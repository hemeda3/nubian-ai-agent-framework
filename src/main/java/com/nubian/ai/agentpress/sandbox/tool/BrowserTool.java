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
public class BrowserTool extends SandboxToolBase {

    @Autowired
    public BrowserTool(String projectId) {
        super(projectId);
    }

    @ToolFunction(name = "browser-action", description = "Performs an action in the browser.")
    @OpenApiSchema(value = """
        {
          "type": "object",
          "properties": {
            "action": {
              "type": "string",
              "description": "The action to perform (e.g., launch, click, type, scroll_down, scroll_up, close).",
              "enum": ["launch", "click", "type", "scroll_down", "scroll_up", "close"]
            },
            "url": {
              "type": "string",
              "description": "URL to launch the browser at (optional, for launch action)."
            },
            "coordinate": {
              "type": "string",
              "description": "X and Y coordinates for the click action (e.g., '450,300')."
            },
            "text": {
              "type": "string",
              "description": "Text to type (for type action)."
            }
          },
          "required": ["action"]
        }
        """)
    public CompletableFuture<ToolResult> browserAction(ToolExecutionContext context, String action, String url, String coordinate, String text) {
        log.info("BrowserTool: Performing action: {}", action);
        // Check if the real implementation is available
        if (sandboxService == null) {
            throw new IllegalStateException("Sandbox service not available for browser actions");
        }
        
        // For now, this is not implemented yet, so throw an exception
        throw new UnsupportedOperationException("Browser interaction is not yet implemented");
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
