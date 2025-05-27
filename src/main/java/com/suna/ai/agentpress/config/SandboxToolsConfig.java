package com.Nubian.ai.agentpress.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.Nubian.ai.agentpress.sandbox.SandboxFileService;
import com.Nubian.ai.agentpress.sandbox.service.SandboxService;
import com.Nubian.ai.agentpress.sandbox.service.WorkspaceService;
import com.Nubian.ai.agentpress.sandbox.tool.BrowserTool;
import com.Nubian.ai.agentpress.sandbox.tool.DeployTool;
import com.Nubian.ai.agentpress.sandbox.tool.ExposeTool;
import com.Nubian.ai.agentpress.sandbox.tool.FileTool;
import com.Nubian.ai.agentpress.sandbox.tool.ProcessTool;
import com.Nubian.ai.agentpress.sandbox.tool.ResilientFileTool;
import com.Nubian.ai.agentpress.sandbox.tool.VisionTool;
import com.Nubian.ai.agentpress.sandbox.tool.WorkspaceTool;
import com.Nubian.ai.agentpress.service.ContextManager;
import com.Nubian.ai.agentpress.service.DBConnection;
import com.Nubian.ai.agentpress.service.ThreadManager;

/**
 * Configuration for sandbox tools.
 */
@Configuration
public class SandboxToolsConfig {

    @Autowired
    private SandboxService sandboxService;
    
    @Autowired
    private SandboxFileService sandboxFileService;
    
    @Autowired
    private WorkspaceService workspaceService;
    
    @Autowired
    private ThreadManager threadManager;
    
    @Autowired
    private ContextManager contextManager;
    
    @Autowired
    private DBConnection dbConnection;

    /**
     * Create a default project ID for development.
     * 
     * @return The default project ID
     */
    @Bean
    public String defaultProjectId() {
        return "default";
    }

    /**
     * Create a BrowserTool bean.
     * 
     * @param projectId The project ID
     * @return The BrowserTool
     */
    @Bean
    @Primary
    public BrowserTool browserTool(String projectId) {
        BrowserTool tool = new BrowserTool(projectId);
        initializeToolDependencies(tool);
        return tool;
    }

    /**
     * Create a FileTool bean.
     * 
     * @param projectId The project ID
     * @return The FileTool
     */
    @Bean
    @Primary
    public FileTool fileTool(String projectId) {
        FileTool tool = new ResilientFileTool(projectId);
        initializeToolDependencies(tool);
        return tool;
    }

    /**
     * Create a ProcessTool bean.
     * 
     * @param projectId The project ID
     * @return The ProcessTool
     */
    @Bean
    @Primary
    public ProcessTool processTool(String projectId) {
        ProcessTool tool = new ProcessTool(projectId);
        initializeToolDependencies(tool);
        return tool;
    }

    /**
     * Create a WorkspaceTool bean.
     * 
     * @param projectId The project ID
     * @return The WorkspaceTool
     */
    @Bean
    @Primary
    public WorkspaceTool workspaceTool(String projectId) {
        WorkspaceTool tool = new WorkspaceTool(projectId);
        initializeToolDependencies(tool);
        return tool;
    }

    /**
     * Create a VisionTool bean.
     * 
     * @param projectId The project ID
     * @return The VisionTool
     */
    @Bean
    @Primary
    public VisionTool visionTool(String projectId) {
        VisionTool tool = new VisionTool(projectId);
        initializeToolDependencies(tool);
        return tool;
    }

    /**
     * Create a DeployTool bean.
     * 
     * @param projectId The project ID
     * @return The DeployTool
     */
    @Bean
    @Primary
    public DeployTool deployTool(String projectId) {
        DeployTool tool = new DeployTool(projectId);
        initializeToolDependencies(tool);
        return tool;
    }

    /**
     * Create an ExposeTool bean.
     * 
     * @param projectId The project ID
     * @return The ExposeTool
     */
    @Bean
    @Primary
    public ExposeTool exposeTool(String projectId) {
        ExposeTool tool = new ExposeTool(projectId);
        initializeToolDependencies(tool);
        return tool;
    }
    
    /**
     * Initialize common dependencies for all sandbox tools.
     * 
     * @param tool The tool to initialize
     */
    private void initializeToolDependencies(com.Nubian.ai.agentpress.sandbox.tool.SandboxToolBase tool) {
        try {
            // Safely set each dependency with null checks
            if (sandboxService != null) {
                tool.setSandboxService(sandboxService);
            } else {
                logMissingDependency("sandboxService");
            }
            
            if (sandboxFileService != null) {
                tool.setSandboxFileService(sandboxFileService);
            } else {
                logMissingDependency("sandboxFileService");
            }
            
            if (workspaceService != null) {
                tool.setWorkspaceService(workspaceService);
            } else {
                logMissingDependency("workspaceService");
            }
            
            if (threadManager != null) {
                tool.setThreadManager(threadManager);
            } else {
                logMissingDependency("threadManager");
            }
            
            if (contextManager != null) {
                tool.setContextManager(contextManager);
            } else {
                logMissingDependency("contextManager");
            }
            
            if (dbConnection != null) {
                tool.setDbConnection(dbConnection);
            } else {
                logMissingDependency("dbConnection");
            }
        } catch (Exception e) {
            // Log any exceptions during initialization but don't fail
            org.slf4j.LoggerFactory.getLogger(SandboxToolsConfig.class)
                .error("Error initializing tool dependencies: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Log a message when a dependency is missing.
     * 
     * @param dependencyName The name of the missing dependency
     */
    private void logMissingDependency(String dependencyName) {
        org.slf4j.LoggerFactory.getLogger(SandboxToolsConfig.class)
            .warn("Missing dependency: {} is null during tool initialization", dependencyName);
    }
}
