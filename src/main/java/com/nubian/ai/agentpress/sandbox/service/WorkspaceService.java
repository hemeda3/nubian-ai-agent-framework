package com.nubian.ai.agentpress.sandbox.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.nubian.ai.agentpress.sandbox.client.HttpClientService;
import com.nubian.ai.agentpress.sandbox.model.DaytonaWorkspace;

import java.util.List; // Added import
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service for managing Daytona workspaces.
 */
@Service
public class WorkspaceService {
    private static final Logger logger = LoggerFactory.getLogger(WorkspaceService.class);
    
    private final HttpClientService httpClient;
    
    @Autowired
    public WorkspaceService(HttpClientService httpClient) {
        this.httpClient = httpClient;
    }
    
    /**
     * Create a new workspace with specified parameters.
     *
     * @param creationParams The parameters for creating the workspace
     * @param organizationId Optional organization ID
     * @return CompletableFuture containing the created workspace
     */
    public CompletableFuture<DaytonaWorkspace> createWorkspace(Map<String, Object> creationParams, String organizationId) {
        logger.info("Creating Daytona workspace with params: {}", creationParams);
        return httpClient.post("/workspace", creationParams, DaytonaWorkspace.class, organizationId);
    }
    
    /**
     * Get details about a workspace.
     *
     * @param workspaceId The workspace ID
     * @param organizationId Optional organization ID
     * @return CompletableFuture containing the workspace details
     */
    public CompletableFuture<DaytonaWorkspace> getWorkspaceDetails(String workspaceId, String organizationId) {
        logger.info("Fetching details for workspace: {}", workspaceId);
        return httpClient.get("/workspace/" + workspaceId, DaytonaWorkspace.class, organizationId);
    }
    
    /**
     * Delete a workspace.
     *
     * @param workspaceId The workspace ID
     * @param force Whether to force deletion
     * @param organizationId Optional organization ID
     * @return CompletableFuture that completes when the workspace is deleted
     */
    public CompletableFuture<Void> deleteWorkspace(String workspaceId, boolean force, String organizationId) {
        logger.info("Deleting workspace: {} with force={}", workspaceId, force);
        String path = "/workspace/" + workspaceId + (force ? "?force=true" : "");
        return httpClient.delete(path, Void.class, organizationId);
    }
    
    /**
     * Start a workspace.
     *
     * @param workspaceId The workspace ID
     * @param organizationId Optional organization ID
     * @return CompletableFuture that completes when the workspace is started
     */
    public CompletableFuture<Void> startWorkspace(String workspaceId, String organizationId) {
        logger.info("Starting workspace: {}", workspaceId);
        return httpClient.post("/workspace/" + workspaceId + "/start", null, Void.class, organizationId);
    }
    
    /**
     * Stop a workspace.
     *
     * @param workspaceId The workspace ID
     * @param organizationId Optional organization ID
     * @return CompletableFuture that completes when the workspace is stopped
     */
    public CompletableFuture<Void> stopWorkspace(String workspaceId, String organizationId) {
        logger.info("Stopping workspace: {}", workspaceId);
        return httpClient.post("/workspace/" + workspaceId + "/stop", null, Void.class, organizationId);
    }
    
    /**
     * Archive a workspace.
     *
     * @param workspaceId The workspace ID
     * @param organizationId Optional organization ID
     * @return CompletableFuture that completes when the workspace is archived
     */
    public CompletableFuture<Void> archiveWorkspace(String workspaceId, String organizationId) {
        logger.info("Archiving workspace: {}", workspaceId);
        return httpClient.post("/workspace/" + workspaceId + "/archive", null, Void.class, organizationId);
    }
    
    /**
     * Get a preview URL for a port on a workspace.
     *
     * @param workspaceId The workspace ID
     * @param port The port to get a preview URL for
     * @param organizationId Optional organization ID
     * @return CompletableFuture containing the preview URL
     */
    public CompletableFuture<String> getPortPreviewUrl(String workspaceId, int port, String organizationId) {
        logger.info("Getting preview URL for workspace: {}, port: {}", workspaceId, port);
        return httpClient.get("/workspace/" + workspaceId + "/ports/" + port + "/preview-url", String.class, organizationId);
    }

    /**
     * List all workspaces.
     *
     * @param organizationId Optional organization ID
     * @return CompletableFuture containing a list of workspaces
     */
    public CompletableFuture<List<DaytonaWorkspace>> listWorkspaces(String organizationId) {
        logger.info("Listing workspaces for organization: {}", organizationId);
        // Assuming the /workspace endpoint returns a list of workspaces
        return httpClient.get("/workspace", new com.fasterxml.jackson.core.type.TypeReference<List<DaytonaWorkspace>>() {}, organizationId);
    }
}
