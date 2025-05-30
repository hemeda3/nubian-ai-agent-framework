package com.nubian.ai.agentpress.sandbox.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.nubian.ai.agentpress.sandbox.model.DaytonaWorkspace;

import java.util.concurrent.CompletableFuture;

/**
 * Service for managing sandbox sessions and process initialization.
 */
@Service
public class SandboxSessionService {
    private static final Logger logger = LoggerFactory.getLogger(SandboxSessionService.class);
    
    private final ProcessService processService;
    
    @Autowired
    public SandboxSessionService(ProcessService processService) {
        this.processService = processService;
    }
    
    /**
     * Start supervisord in a session.
     *
     * @param workspace The workspace to start supervisord in
     * @param organizationId Optional organization ID
     * @return CompletableFuture that completes when supervisord is started
     */
    public CompletableFuture<Void> startSupervisordSession(DaytonaWorkspace workspace, String organizationId) {
        String workspaceId = workspace.getId();
        
        logger.info("Creating session for supervisord in workspace {}", workspaceId);
        
        return processService.createProcessSession(workspaceId, organizationId)
            .thenCompose(session -> {
                // Execute supervisord command
                String supervisordCommand = "/usr/bin/supervisord -n -c /etc/supervisor/conf.d/supervisord.conf";
                
                return processService.executeCommandInSession(
                        workspaceId, session.getId(), supervisordCommand, true, null, null, organizationId)
                    .thenApply(cmd -> null);
            });
    }
    
    /**
     * Execute a command in a workspace.
     *
     * @param workspaceId The workspace ID
     * @param command The command to execute
     * @param async Whether to execute asynchronously
     * @param organizationId Optional organization ID
     * @return CompletableFuture containing the command execution result
     */
    public CompletableFuture<String> executeCommand(
            String workspaceId, String command, boolean async, String organizationId) {
        logger.info("Executing command in workspace {}: {}", workspaceId, command);
        
        return processService.createProcessSession(workspaceId, organizationId)
            .thenCompose(session -> 
                processService.executeCommandInSession(
                    workspaceId, session.getId(), command, async, null, null, organizationId)
                .thenCompose(cmdExec -> {
                    if (!async) {
                        return processService.getCommandLogs(
                            workspaceId, session.getId(), cmdExec.getId(), false, organizationId);
                    } else {
                        return CompletableFuture.completedFuture("Command started asynchronously");
                    }
                })
                .whenComplete((result, error) -> {
                    // Clean up the session
                    processService.deleteProcessSession(workspaceId, session.getId(), organizationId)
                        .exceptionally(e -> {
                            logger.warn("Failed to delete process session: {}", e.getMessage());
                            return null;
                        });
                })
            );
    }
}
