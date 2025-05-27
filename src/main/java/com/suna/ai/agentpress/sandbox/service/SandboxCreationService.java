package com.Nubian.ai.agentpress.sandbox.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.Nubian.ai.agentpress.sandbox.model.DaytonaWorkspace;
import com.Nubian.ai.agentpress.util.config.Configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service for creating sandboxes.
 */
@Service
public class SandboxCreationService {
    private static final Logger logger = LoggerFactory.getLogger(SandboxCreationService.class);
    
    private final WorkspaceService workspaceService;
    private final SandboxSessionService sessionService;
    private final String sandboxImageName;
    
    @Autowired
    public SandboxCreationService(
            WorkspaceService workspaceService,
            SandboxSessionService sessionService,
            @Value("${sandbox.image.name:Nubian/browser-sandbox:latest}") String sandboxImageName) {
        this.workspaceService = workspaceService;
        this.sessionService = sessionService;
        this.sandboxImageName = sandboxImageName;
        
        logger.info("SandboxCreationService initialized with image name: {}", sandboxImageName);
    }
    
    /**
     * Create a new sandbox.
     *
     * @param password The password for VNC access
     * @param projectId Optional project ID
     * @return CompletableFuture containing the new sandbox
     */
    public CompletableFuture<DaytonaWorkspace> createSandbox(String password, String projectId) {
        return createSandbox(password, projectId, null);
    }
    
    /**
     * Create a new sandbox.
     *
     * @param password The password for VNC access
     * @param projectId Optional project ID
     * @param organizationId Optional organization ID
     * @return CompletableFuture containing the new sandbox
     */
    public CompletableFuture<DaytonaWorkspace> createSandbox(
            String password, String projectId, String organizationId) {
        
        logger.debug("Creating new Daytona sandbox environment");
        
        Map<String, String> labels = null;
        if (projectId != null && !projectId.isEmpty()) {
            logger.debug("Using sandbox_id as label: {}", projectId);
            labels = new HashMap<>();
            labels.put("id", projectId);
        }
        
        Map<String, Object> creationParams = buildSandboxCreationParams(password, projectId, labels);
        
        return workspaceService.createWorkspace(creationParams, organizationId)
            .thenCompose(workspace -> {
                logger.debug("Sandbox created with ID: {}", workspace.getId());
                
                return sessionService.startSupervisordSession(workspace, organizationId)
                    .thenApply(v -> {
                        logger.debug("Sandbox environment successfully initialized");
                        return workspace;
                    });
            });
    }
    
    /**
     * Build creation parameters for a sandbox.
     */
    private Map<String, Object> buildSandboxCreationParams(String password, String projectId, Map<String, String> labels) {
        Map<String, Object> creationParams = new HashMap<>();
        creationParams.put("name", "sandbox-" + (projectId != null ? projectId : System.currentTimeMillis()));
        creationParams.put("image", sandboxImageName);
        creationParams.put("isPublic", true);
        
        if (labels != null) {
            creationParams.put("labels", labels);
        }
        
        // Environment variables
        Map<String, String> envVars = new HashMap<>();
        envVars.put("CHROME_PERSISTENT_SESSION", "true");
        envVars.put("RESOLUTION", "1024x768x24");
        envVars.put("RESOLUTION_WIDTH", "1024");
        envVars.put("RESOLUTION_HEIGHT", "768");
        envVars.put("VNC_PASSWORD", password);
        envVars.put("ANONYMIZED_TELEMETRY", "false");
        envVars.put("CHROME_PATH", "");
        envVars.put("CHROME_USER_DATA", "");
        envVars.put("CHROME_DEBUGGING_PORT", "9222");
        envVars.put("CHROME_DEBUGGING_HOST", "localhost");
        envVars.put("CHROME_CDP", "");
        creationParams.put("env", envVars);
        
        // Resources
        Map<String, Integer> resources = new HashMap<>();
        resources.put("cpu", 2);
        resources.put("memory", 4);
        resources.put("disk", 5);
        creationParams.put("resources", resources);
        
        return creationParams;
    }
}
