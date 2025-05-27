package com.Nubian.ai.agent.service.manager;

import java.util.Collections;
import java.util.List;

import com.Nubian.ai.agent.service.AgentRunnerService;
import com.Nubian.ai.agentpress.service.ContextManager;
import com.Nubian.ai.agentpress.service.DBConnection;
import com.Nubian.ai.agentpress.service.ThreadManager;
import com.Nubian.ai.agentpress.service.ToolRegistry;

import lombok.extern.slf4j.Slf4j;

/**
 * Manages tool registration for agent runs.
 */
@Slf4j
public class ToolRegistrationManager {

    private final ToolRegistry toolRegistry;
    private final AgentRunnerService agentRunnerService;
    private final ThreadManager threadManager;
    private final ContextManager contextManager;
    private final DBConnection dbConnection;

    /**
     * Initialize ToolRegistrationManager.
     *
     * @param toolRegistry The tool registry
     * @param agentRunnerService The agent runner service
     * @param threadManager The thread manager
     * @param contextManager The context manager
     * @param dbConnection The database connection
     */
    public ToolRegistrationManager(
            ToolRegistry toolRegistry,
            AgentRunnerService agentRunnerService,
            ThreadManager threadManager,
            ContextManager contextManager,
            DBConnection dbConnection) {
        this.toolRegistry = toolRegistry;
        this.agentRunnerService = agentRunnerService;
        this.threadManager = threadManager;
        this.contextManager = contextManager;
        this.dbConnection = dbConnection;
    }

    /**
     * Register all tools required for the agent's workflow.
     * 
     * @param threadId The thread ID
     * @param projectId The project ID
     */
    public void registerAgentTools(String threadId, String projectId) {
        // Create ObjectMapper instance
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper(); // ADDED

        // Create agent-specific tools
        com.Nubian.ai.agent.tool.DataProviderTool dataProviderTool = 
            new com.Nubian.ai.agent.tool.DataProviderTool(objectMapper); // MODIFIED
        com.Nubian.ai.agent.tool.MessageTool messageTool = new com.Nubian.ai.agent.tool.MessageTool(objectMapper); // MODIFIED
        
        // Create sandbox tools
        com.Nubian.ai.agentpress.sandbox.tool.FileTool fileTool = 
            new com.Nubian.ai.agentpress.sandbox.tool.FileTool(projectId);
        fileTool.setSandboxService(agentRunnerService.getSandboxService());
        fileTool.setSandboxFileService(agentRunnerService.getSandboxFileService());
        fileTool.setThreadManager(threadManager);
        fileTool.setContextManager(contextManager);
        fileTool.setDbConnection(dbConnection);
        fileTool.setWorkspaceService(agentRunnerService.getWorkspaceService());
        
        com.Nubian.ai.agentpress.sandbox.tool.ProcessTool processTool = 
            new com.Nubian.ai.agentpress.sandbox.tool.ProcessTool(projectId);
        processTool.setSandboxService(agentRunnerService.getSandboxService());
        processTool.setThreadManager(threadManager);
        processTool.setContextManager(contextManager);
        processTool.setDbConnection(dbConnection);
        processTool.setWorkspaceService(agentRunnerService.getWorkspaceService());
        
        com.Nubian.ai.agentpress.sandbox.tool.WorkspaceTool workspaceTool = 
            new com.Nubian.ai.agentpress.sandbox.tool.WorkspaceTool(projectId);
        workspaceTool.setSandboxService(agentRunnerService.getSandboxService());
        workspaceTool.setSandboxFileService(agentRunnerService.getSandboxFileService());
        workspaceTool.setThreadManager(threadManager);
        workspaceTool.setContextManager(contextManager);
        workspaceTool.setDbConnection(dbConnection);
        workspaceTool.setWorkspaceService(agentRunnerService.getWorkspaceService());
        
        com.Nubian.ai.agentpress.sandbox.tool.BrowserTool browserTool = 
            new com.Nubian.ai.agentpress.sandbox.tool.BrowserTool(projectId);
        browserTool.setSandboxService(agentRunnerService.getSandboxService());
        browserTool.setThreadManager(threadManager);
        browserTool.setContextManager(contextManager);
        browserTool.setDbConnection(dbConnection);
        browserTool.setWorkspaceService(agentRunnerService.getWorkspaceService());
        
        com.Nubian.ai.agentpress.sandbox.tool.VisionTool visionTool = 
            new com.Nubian.ai.agentpress.sandbox.tool.VisionTool(projectId);
        visionTool.setSandboxService(agentRunnerService.getSandboxService());
        visionTool.setThreadManager(threadManager);
        visionTool.setContextManager(contextManager);
        visionTool.setDbConnection(dbConnection);
        visionTool.setWorkspaceService(agentRunnerService.getWorkspaceService());
        
        com.Nubian.ai.agentpress.sandbox.tool.DeployTool deployTool = 
            new com.Nubian.ai.agentpress.sandbox.tool.DeployTool(projectId);
        deployTool.setSandboxService(agentRunnerService.getSandboxService());
        deployTool.setThreadManager(threadManager);
        deployTool.setContextManager(contextManager);
        deployTool.setDbConnection(dbConnection);
        deployTool.setWorkspaceService(agentRunnerService.getWorkspaceService());
        
        com.Nubian.ai.agentpress.sandbox.tool.ExposeTool exposeTool = 
            new com.Nubian.ai.agentpress.sandbox.tool.ExposeTool(projectId);
        exposeTool.setSandboxService(agentRunnerService.getSandboxService());
        exposeTool.setThreadManager(threadManager);
        exposeTool.setContextManager(contextManager);
        exposeTool.setDbConnection(dbConnection);
        exposeTool.setWorkspaceService(agentRunnerService.getWorkspaceService());
        
        // Register all tools with the tool registry - using Collections.singletonList to wrap the threadId
        toolRegistry.registerTool(dataProviderTool, Collections.singletonList(threadId));
        toolRegistry.registerTool(messageTool, Collections.singletonList(threadId));
        toolRegistry.registerTool(fileTool, Collections.singletonList(threadId));
        toolRegistry.registerTool(processTool, Collections.singletonList(threadId));
        toolRegistry.registerTool(workspaceTool, Collections.singletonList(threadId));
        toolRegistry.registerTool(browserTool, Collections.singletonList(threadId));
        toolRegistry.registerTool(visionTool, Collections.singletonList(threadId));
        toolRegistry.registerTool(deployTool, Collections.singletonList(threadId));
        toolRegistry.registerTool(exposeTool, Collections.singletonList(threadId));
        
        log.info("Registered all tools for thread {}, project {}", threadId, projectId);
    }
}
