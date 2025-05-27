package com.Nubian.ai.agentpress.sandbox.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.Nubian.ai.agentpress.sandbox.model.DaytonaWorkspace;

import java.util.List; // Added import
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * High-level service for managing sandboxes.
 * This service coordinates specialized services to provide a unified API.
 */
@Service
public class SandboxService {
    private static final Logger logger = LoggerFactory.getLogger(SandboxService.class);
    
    private final WorkspaceService workspaceService;
    private final SandboxCreationService creationService;
    private final SandboxSessionService sessionService;
    private final SandboxLifecycleService lifecycleService;
    
    @Autowired
    public SandboxService(
            WorkspaceService workspaceService,
            SandboxCreationService creationService,
            SandboxSessionService sessionService,
            SandboxLifecycleService lifecycleService) {
        
        this.workspaceService = workspaceService;
        this.creationService = creationService;
        this.sessionService = sessionService;
        this.lifecycleService = lifecycleService;
        
        logger.info("SandboxService initialized");
    }
    
    /**
     * Get a sandbox by ID or start it if it's not running.
     *
     * @param sandboxId The sandbox ID
     * @return CompletableFuture containing the sandbox
     */
    public CompletableFuture<DaytonaWorkspace> getOrStartSandbox(String sandboxId) {
        return lifecycleService.getOrStartSandbox(sandboxId, null, null); // Pass null for organizationId and sandboxPass
    }
    
    /**
     * Get a sandbox by ID or start it if it's not running.
     *
     * @param sandboxId The sandbox ID
     * @param organizationId Optional organization ID
     * @return CompletableFuture containing the sandbox
     */
    public CompletableFuture<DaytonaWorkspace> getOrStartSandbox(String sandboxId, String organizationId) {
        return lifecycleService.getOrStartSandbox(sandboxId, organizationId, null); // Pass null for sandboxPass
    }
    
    /**
     * Get a sandbox by ID or start it if it's not running, with a specific password.
     *
     * @param sandboxId The sandbox ID
     * @param organizationId Optional organization ID
     * @param sandboxPass The password for the sandbox (if applicable)
     * @return CompletableFuture containing the sandbox
     */
    public CompletableFuture<DaytonaWorkspace> getOrStartSandbox(String sandboxId, String organizationId, String sandboxPass) {
        return lifecycleService.getOrStartSandbox(sandboxId, organizationId, sandboxPass);
    }
    
    /**
     * Create a new sandbox.
     *
     * @param password The password for VNC access
     * @param projectId Optional project ID
     * @return CompletableFuture containing the new sandbox
     */
    public CompletableFuture<DaytonaWorkspace> createSandbox(String password, String projectId) {
        return creationService.createSandbox(password, projectId, null);
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
        
        return creationService.createSandbox(password, projectId, organizationId);
    }
    
    /**
     * Delete a sandbox.
     *
     * @param sandboxId The sandbox ID
     * @return CompletableFuture that completes when the sandbox is deleted
     */
    public CompletableFuture<Void> deleteSandbox(String sandboxId) {
        return lifecycleService.deleteSandbox(sandboxId, null);
    }
    
    /**
     * Delete a sandbox.
     *
     * @param sandboxId The sandbox ID
     * @param organizationId Optional organization ID
     * @return CompletableFuture that completes when the sandbox is deleted
     */
    public CompletableFuture<Void> deleteSandbox(String sandboxId, String organizationId) {
        return lifecycleService.deleteSandbox(sandboxId, organizationId);
    }
    
    /**
     * Get information about a sandbox.
     *
     * @param sandboxId The sandbox ID
     * @return CompletableFuture containing information about the sandbox
     */
    public CompletableFuture<Map<String, Object>> getSandboxInfo(String sandboxId) {
        return lifecycleService.getSandboxInfo(sandboxId, null);
    }
    
    /**
     * Get information about a sandbox.
     *
     * @param sandboxId The sandbox ID
     * @param organizationId Optional organization ID
     * @return CompletableFuture containing information about the sandbox
     */
    public CompletableFuture<Map<String, Object>> getSandboxInfo(String sandboxId, String organizationId) {
        return lifecycleService.getSandboxInfo(sandboxId, organizationId);
    }
    
    /**
     * Execute a command in a sandbox.
     *
     * @param sandboxId The sandbox ID
     * @param command The command to execute
     * @param async Whether to execute asynchronously
     * @return CompletableFuture containing the command execution result
     */
    public CompletableFuture<String> executeCommand(String sandboxId, String command, boolean async) {
        return sessionService.executeCommand(sandboxId, command, async, null);
    }
    
    /**
     * Execute a command in a sandbox.
     *
     * @param sandboxId The sandbox ID
     * @param command The command to execute
     * @param async Whether to execute asynchronously
     * @param organizationId Optional organization ID
     * @return CompletableFuture containing the command execution result
     */
    public CompletableFuture<String> executeCommand(
            String sandboxId, String command, boolean async, String organizationId) {
        return sessionService.executeCommand(sandboxId, command, async, organizationId);
    }

    /**
     * List all workspaces.
     *
     * @param organizationId Optional organization ID
     * @return CompletableFuture containing a list of workspaces
     */
    public CompletableFuture<List<DaytonaWorkspace>> listWorkspaces(String organizationId) {
        logger.info("Listing workspaces via SandboxService for organization: {}", organizationId);
        return workspaceService.listWorkspaces(organizationId);
    }
}
