package com.Nubian.ai.agent.controller;

import com.Nubian.ai.agent.model.AgentRunRequest;
import com.Nubian.ai.agent.model.AgentRunResponse;
import com.Nubian.ai.agent.model.AgentRunStatus;
import com.Nubian.ai.agent.service.AgentRunManager;
import com.Nubian.ai.agentpress.service.ContextManager;
import com.Nubian.ai.agentpress.service.ThreadManager;

import com.Nubian.ai.agent.service.helper.AgentRedisHelper;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.Nubian.ai.agentpress.service.DBConnection;
import com.Nubian.ai.agentpress.util.auth.JwtUtils;
import com.Nubian.ai.agentpress.service.AccountService;
import com.Nubian.ai.agentpress.service.ProjectService;
import com.Nubian.ai.agentpress.model.Account;
import com.Nubian.ai.agentpress.model.Project;
import com.Nubian.ai.agentpress.sandbox.service.SandboxService;
import com.Nubian.ai.agentpress.sandbox.SandboxFileService; // Import SandboxFileService
import com.Nubian.ai.agentpress.sandbox.model.DaytonaWorkspace;
import com.Nubian.ai.agent.service.AgentBackgroundService; // Import AgentBackgroundService
import com.Nubian.ai.agentpress.model.Message; // Import Message for initial prompt

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;

/**
 * REST controller for agent operations.
 * 
 * This controller provides endpoints for interacting with AI agents, including:
 * - Starting agent runs
 * - Checking agent status
 * - Stopping agents
 * - Getting agent results
 * 
 * The controller integrates with the core AgentPress framework components like
 * ThreadManager and ContextManager while providing agent-specific functionality.
 */
@RestController
@RequestMapping("/api/agent")
@Slf4j
public class AgentController {

    private final AgentRunManager agentRunManager;
    private final ThreadManager threadManager;
    private final ContextManager contextManager;
    private final AgentRedisHelper agentRedisHelper;
    private final DBConnection dbConnection;
    private final JwtUtils jwtUtils;
    private final AccountService accountService;
    private final ProjectService projectService;
    private final SandboxService sandboxService;
    private final SandboxFileService sandboxFileService;
    private final AgentBackgroundService agentBackgroundService;

    @Autowired
    public AgentController(AgentRunManager agentRunManager, 
                          ThreadManager threadManager,
                          ContextManager contextManager,
                          AgentRedisHelper agentRedisHelper,
                          DBConnection dbConnection,
                          JwtUtils jwtUtils,
                          AccountService accountService,
                          ProjectService projectService,
                          SandboxService sandboxService,
                          SandboxFileService sandboxFileService,
                          AgentBackgroundService agentBackgroundService) {
        this.agentRunManager = agentRunManager;
        this.threadManager = threadManager;
        this.contextManager = contextManager;
        this.agentRedisHelper = agentRedisHelper;
        this.dbConnection = dbConnection;
        this.jwtUtils = jwtUtils;
        this.accountService = accountService;
        this.projectService = projectService;
        this.sandboxService = sandboxService;
        this.sandboxFileService = sandboxFileService;
        this.agentBackgroundService = agentBackgroundService;
    }
    
    /**
     * Stream updates for a specific agent run.
     * 
     * @param agentRunId The agent run ID
     * @return SseEmitter for streaming updates
     */
    @GetMapping(value = "/runs/{agentRunId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAgentRun(@PathVariable String agentRunId) {
        log.info("Client connected for streaming agent run: {}", agentRunId);
        
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE); // Use a long timeout
        
        // Subscribe to the Redis stream for this agent run
        agentRedisHelper.subscribeToRunStream(agentRunId, message -> {
            try {
                // Send the message to the client
                emitter.send(SseEmitter.event().data(message));
            } catch (Exception e) {
                log.error("Error sending message to SSE emitter for run {}: {}", agentRunId, e.getMessage(), e);
                // If sending fails, complete the emitter and unsubscribe
                emitter.completeWithError(e);
            }
        });
        
        // Handle emitter completion and timeout
        emitter.onCompletion(() -> {
            log.info("SSE emitter completed for agent run: {}", agentRunId);
            // Unsubscribe from Redis when the emitter is completed
            agentRedisHelper.unsubscribeFromRunStream(agentRunId);
        });
        
        emitter.onTimeout(() -> {
            log.warn("SSE emitter timed out for agent run: {}", agentRunId);
            // Complete the emitter and unsubscribe on timeout
            agentRedisHelper.unsubscribeFromRunStream(agentRunId);
            emitter.complete();
        });
        
        emitter.onError(e -> {
            log.error("SSE emitter error for agent run {}: {}", agentRunId, e.getMessage(), e);
            // Unsubscribe and complete on error
            agentRedisHelper.unsubscribeFromRunStream(agentRunId);
            emitter.completeWithError(e);
        });
        
        return emitter;
    }

    /**
     * Start a new agent run with full project/thread/sandbox orchestration.
     * 
     * This endpoint handles the complete workflow for starting an agent run:
     * 1. Performs authentication/authorization using JwtUtils
     * 2. Manages account and project records via AccountService and ProjectService
     * 3. Provisions/ensures a Daytona sandbox via SandboxService
     * 4. Creates a thread record in the database via ThreadManager
     * 5. Handles file uploads to the sandbox using SandboxFileService
     * 6. Constructs and adds the initial user message to the thread
     * 7. Submits the agent run to the background service for processing
     * 
     * @param authHeader Authentication token in the Authorization header
     * @param request The agent run request containing configuration
     * @param files Optional files to upload to the agent's sandbox
     * @return Response containing the agent run ID and thread ID
     */
    @PostMapping(value = "/runs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AgentRunResponse> startAgent(
            @RequestHeader("Authorization") String authHeader,
            @RequestPart("request") AgentRunRequest request,
            @RequestPart(value = "files", required = false) MultipartFile[] files) {
        
        // Input validation using Spring validation annotations would be added here
        // using @Valid and custom validators
        
        log.info("Starting new agent run with model: {}", request.getModelName());
        if (files != null) {
            log.info("Received {} files for agent run", files.length);
        }
        
        try {
            // 1. Authentication & Authorization: Get userId from authHeader
            String userId = jwtUtils.getUserIdFromToken(authHeader);
            log.info("Authenticated user ID: {}", userId);

            // 2. Get or create an accounts record for the authenticated user.
            Account account = accountService.getOrCreateAccountForUser(userId).join(); // Block to get result
            String accountId = account.getId();
            log.info("Using account ID: {}", accountId);

            // 3. Create a projects record (linking to account_id), or use an existing one.
            // For now, we'll create a new project for each run if not specified, or use a default.
            // The task implies using an existing one or creating. Let's create a new one for each run for now.
            Project project = projectService.getOrCreateProjectForAccount(accountId, userId).join(); // Block to get result
            String projectId = project.getId();
            log.info("Using project ID: {}", projectId);

            // 4. Create/ensure a sandbox via SandboxService and store its ID/details in the projects.sandbox JSONB column.
            // For now, using dummy values for password and organizationId.
            String dummyPassword = "dummy_password"; // TODO: Replace with actual password generation/retrieval
            String dummyOrganizationId = "default_org"; // TODO: Replace with actual organization ID
            
            // Asynchronously create sandbox and then update project with sandbox ID
            DaytonaWorkspace workspace = sandboxService.createSandbox(dummyPassword, projectId, dummyOrganizationId).join(); // .join() blocks until completion
            String sandboxId = workspace.getId();
            projectService.updateProjectSandboxId(projectId, sandboxId);
            log.info("Provisioned sandbox ID: {} for project {}", sandboxId, projectId);

            // 5. Create a threads record (linking to project_id and account_id).
            com.Nubian.ai.agentpress.model.Thread thread = threadManager.createThread(projectId, accountId);
            String threadId = thread.getId();
            log.info("Created thread ID: {}", threadId);
            
            // 6. Handle file uploads: save them to the sandbox (via SandboxFileService).
            List<String> uploadedFilePaths = new ArrayList<>();
            if (files != null && files.length > 0) {
                for (MultipartFile file : files) {
                    String fileName = file.getOriginalFilename();
                    String sandboxFilePath = "/workspace/" + fileName; // Standard path in sandbox
                    try {
                        sandboxFileService.uploadFile(sandboxId, sandboxFilePath, file.getBytes()).join(); // Blocking for simplicity
                        uploadedFilePaths.add(sandboxFilePath);
                        log.info("Uploaded file {} to sandbox {}", fileName, sandboxId);
                    } catch (IOException e) {
                        log.error("Failed to read file content for upload: {}", fileName, e);
                        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read file: " + fileName);
                    } catch (Exception e) {
                        log.error("Failed to upload file {} to sandbox {}: {}", fileName, sandboxId, e.getMessage(), e);
                        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to upload file: " + fileName);
                    }
                }
            }

            // 7. Construct the initial user message and add to thread.
            StringBuilder initialPromptContent = new StringBuilder(request.getInitialPrompt());
            if (!uploadedFilePaths.isEmpty()) {
                initialPromptContent.append("\n\nUploaded files: ");
                for (String filePath : uploadedFilePaths) {
                    initialPromptContent.append("\n- ").append(filePath);
                }
            }
            
            // Add the initial user message to the new thread
            // Assuming 'user' role for the initial prompt
            threadManager.addMessage(threadId, "user", initialPromptContent.toString(), false, null).join(); // Blocking for simplicity
            log.info("Added initial message to thread {}", threadId);

            // 8. Generate a unique ID for this agent run. (Already done above)
            String agentRunId = UUID.randomUUID().toString(); // Re-declare for clarity, though already in scope

            // 9. Call agentBackgroundService.submitAgentRun(...)
            agentBackgroundService.submitAgentRun(agentRunId, request, threadId, projectId);
            log.info("Submitted agent run {} for thread {} and project {}", agentRunId, threadId, projectId);
            
            // Return the agent run ID and thread ID to the client
            AgentRunResponse response = AgentRunResponse.running(agentRunId);
            response.setThreadId(threadId);
            
            return ResponseEntity.ok(response);
        } catch (ResponseStatusException e) {
            log.error("Authentication/Authorization or orchestration error: {}", e.getReason());
            return ResponseEntity.status(e.getStatusCode()).body(
                AgentRunResponse.failed(null, "Operation failed: " + e.getReason())
            );
        } catch (Exception e) {
            log.error("Error starting agent run: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(
                AgentRunResponse.failed(null, "Failed to start agent run: " + e.getMessage())
            );
        }
    }

    /**
     * Get the status of an agent run.
     * 
     * @param agentRunId The agent run ID
     * @return Response containing the agent run status
     */
    @GetMapping("/runs/{agentRunId}")
    public ResponseEntity<AgentRunResponse> getAgentStatus(@PathVariable String agentRunId) {
        log.info("Getting status for agent run: {}", agentRunId);
        
        try {
            AgentRunStatus status = agentRunManager.getAgentRunStatus(agentRunId);
            AgentRunResponse response;
            
            switch (status) {
                case RUNNING:
                    response = AgentRunResponse.running(agentRunId);
                    break;
                case COMPLETED:
                    response = AgentRunResponse.completed(agentRunId);
                    break;
                case STOPPED:
                    response = AgentRunResponse.stopped(agentRunId);
                    break;
                case FAILED:
                    String errorMessage = agentRunManager.getAgentRunError(agentRunId);
                    response = AgentRunResponse.failed(agentRunId, errorMessage);
                    break;
                default:
                    return ResponseEntity.notFound().build();
            }
            
            response.setThreadId(agentRunManager.getThreadIdForAgentRun(agentRunId));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting agent status: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(
                AgentRunResponse.failed(agentRunId, "Failed to get agent status: " + e.getMessage())
            );
        }
    }

    /**
     * Stop an agent run.
     * 
     * @param agentRunId The agent run ID
     * @return Response indicating the agent was stopped
     */
    @PostMapping("/runs/{agentRunId}/stop")
    public ResponseEntity<AgentRunResponse> stopAgent(@PathVariable String agentRunId) {
        log.info("Stopping agent run: {}", agentRunId);
        
        try {
            agentRunManager.stopAgentRun(agentRunId);
            AgentRunResponse response = AgentRunResponse.stopped(agentRunId);
            response.setThreadId(agentRunManager.getThreadIdForAgentRun(agentRunId));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error stopping agent run: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(
                AgentRunResponse.failed(agentRunId, "Failed to stop agent run: " + e.getMessage())
            );
        }
    }

    /**
     * Health check endpoint.
     * 
     * @return Health status information
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "ok");
        health.put("timestamp", System.currentTimeMillis());
        health.put("service", "agent-service");
        
        return ResponseEntity.ok(health);
    }
}
