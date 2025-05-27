package com.Nubian.ai.agentpress.sandbox.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.Nubian.ai.agentpress.sandbox.client.HttpClientService;
import com.Nubian.ai.agentpress.sandbox.model.DaytonaCommandExecution;
import com.Nubian.ai.agentpress.sandbox.model.DaytonaProcessSession;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service for managing process sessions and executing commands.
 */
@Service
public class ProcessService {
    private static final Logger logger = LoggerFactory.getLogger(ProcessService.class);
    
    private final HttpClientService httpClient;
    
    @Autowired
    public ProcessService(HttpClientService httpClient) {
        this.httpClient = httpClient;
    }
    
    /**
     * Create a process session.
     *
     * @param workspaceId The workspace ID
     * @param organizationId Optional organization ID
     * @return CompletableFuture containing the created session
     */
    public CompletableFuture<DaytonaProcessSession> createProcessSession(String workspaceId, String organizationId) {
        logger.info("Creating process session for workspace: {}", workspaceId);
        String path = "/toolbox/" + workspaceId + "/toolbox/process/session";
        return httpClient.post(path, null, DaytonaProcessSession.class, organizationId);
    }
    
    /**
     * Delete a process session.
     *
     * @param workspaceId The workspace ID
     * @param sessionId The session ID
     * @param organizationId Optional organization ID
     * @return CompletableFuture that completes when the session is deleted
     */
    public CompletableFuture<Void> deleteProcessSession(String workspaceId, String sessionId, String organizationId) {
        logger.info("Deleting process session: {} in workspace: {}", sessionId, workspaceId);
        String path = "/toolbox/" + workspaceId + "/toolbox/process/session/" + sessionId;
        return httpClient.delete(path, Void.class, organizationId);
    }
    
    /**
     * Execute a command in a session.
     *
     * @param workspaceId The workspace ID
     * @param sessionId The session ID
     * @param command The command to execute
     * @param isAsync Whether to execute the command asynchronously
     * @param workingDir The working directory to execute the command in
     * @param envVars Environment variables to set for the command
     * @param organizationId Optional organization ID
     * @return CompletableFuture containing the command execution result
     */
    public CompletableFuture<DaytonaCommandExecution> executeCommandInSession(
            String workspaceId, String sessionId, String command, boolean isAsync, 
            String workingDir, Map<String, String> envVars, String organizationId) {
        
        logger.info("Executing command in session {} of workspace {}: {}", sessionId, workspaceId, command);
        
        DaytonaCommandExecution.ExecuteRequest requestBody = new DaytonaCommandExecution.ExecuteRequest();
        requestBody.setCommand(command);
        requestBody.setAsync(isAsync);
        
        if (workingDir != null) {
            requestBody.setCwd(workingDir);
        }
        
        if (envVars != null) {
            requestBody.setEnv(envVars);
        }
        
        String path = "/toolbox/" + workspaceId + "/toolbox/process/session/" + sessionId + "/exec";
        return httpClient.post(path, requestBody, DaytonaCommandExecution.class, organizationId);
    }
    
    /**
     * Get logs for a command execution.
     *
     * @param workspaceId The workspace ID
     * @param sessionId The session ID
     * @param commandId The command ID
     * @param follow Whether to follow the logs
     * @param organizationId Optional organization ID
     * @return CompletableFuture containing the command logs
     */
    public CompletableFuture<String> getCommandLogs(
            String workspaceId, String sessionId, String commandId, boolean follow, String organizationId) {
        
        logger.info("Getting logs for command: {} in session: {} of workspace: {}", 
                   commandId, sessionId, workspaceId);
        
        String path = "/toolbox/" + workspaceId + "/toolbox/process/session/" + 
                     sessionId + "/command/" + commandId + "/logs";
        
        if (follow) {
            path += "?follow=true";
        }
        
        return httpClient.get(path, String.class, organizationId);
    }
}
