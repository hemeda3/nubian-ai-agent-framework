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
public class VisionTool extends SandboxToolBase {

    @Autowired
    public VisionTool(String projectId) {
        super(projectId);
    }

    @ToolFunction(name = "vision-analyze", description = "Analyzes an image from a given URL or base64 content.")
    @OpenApiSchema(value = """
        {
          "type": "object",
          "properties": {
            "imageUrl": {
              "type": "string",
              "description": "URL of the image to analyze."
            },
            "base64Content": {
              "type": "string",
              "description": "Base64 encoded content of the image to analyze."
            },
            "question": {
              "type": "string",
              "description": "A specific question to ask about the image."
            }
          },
          "oneOf": [
            {"required": ["imageUrl", "question"]},
            {"required": ["base64Content", "question"]}
          ]
        }
        """)
    public CompletableFuture<ToolResult> visionAnalyze(ToolExecutionContext context, String imageUrl, String base64Content, String question) {
        log.info("VisionTool: Analyzing image. URL: {}, Base64: {}, Question: {}", imageUrl, base64Content != null ? "present" : "absent", question);
        // Check if the real implementation is available
        if (sandboxService == null) {
            throw new IllegalStateException("Sandbox service not available for vision analysis");
        }
        
        // For now, this is not implemented yet, so throw an exception
        throw new UnsupportedOperationException("Vision analysis functionality is not yet implemented");
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
