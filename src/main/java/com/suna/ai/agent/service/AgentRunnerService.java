package com.Nubian.ai.agent.service;

import com.Nubian.ai.agent.config.AgentConfig;
import com.Nubian.ai.agent.model.AgentRunRequest;
import com.Nubian.ai.agent.model.AgentRunStatus;
import com.Nubian.ai.agent.service.helper.AgentContextHelper;
import com.Nubian.ai.agent.service.helper.AgentExecutionHelper;
import com.Nubian.ai.agent.service.helper.AgentPromptHelper;
import com.Nubian.ai.agent.service.helper.AgentRedisHelper;
import com.Nubian.ai.agent.service.helper.AgentExecutionHelper.AgentLoopResult;
import com.Nubian.ai.agentpress.model.LlmMessage;
import com.Nubian.ai.agentpress.model.Message;
import com.Nubian.ai.agentpress.service.ContextManager;
import com.Nubian.ai.agentpress.service.DBConnection;
import com.Nubian.ai.agentpress.sandbox.SandboxFileService;
import com.Nubian.ai.agentpress.sandbox.service.SandboxService;
import com.Nubian.ai.agentpress.sandbox.service.WorkspaceService;
import com.Nubian.ai.agentpress.service.ThreadManager;
import com.Nubian.ai.agentpress.model.ToolResult;
import com.Nubian.ai.agentpress.service.ToolRegistry;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;

import com.Nubian.ai.agent.tool.DataProviderTool;
import com.Nubian.ai.agent.tool.MessageTool;
import com.Nubian.ai.agentpress.sandbox.tool.FileTool;
import com.Nubian.ai.agentpress.sandbox.tool.ProcessTool;
import com.Nubian.ai.agentpress.sandbox.tool.WorkspaceTool;
import com.Nubian.ai.agentpress.sandbox.tool.BrowserTool; // Import BrowserTool
import com.Nubian.ai.agentpress.sandbox.tool.VisionTool; // Import VisionTool
import com.Nubian.ai.agentpress.sandbox.tool.DeployTool; // Import DeployTool
import com.Nubian.ai.agentpress.sandbox.tool.ExposeTool; // Import ExposeTool

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for executing agent runs.
 * Orchestrates the agent execution workflow and integrates with the core AgentPress services.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentRunnerService {
    
    private final ThreadManager threadManager;
    private final ContextManager contextManager;
    private final DBConnection dbConnection;
    private final ToolRegistry toolRegistry;
    private final AgentConfig agentConfig;
    private final RedisMessageListenerContainer redisMessageListenerContainer;
    private final org.springframework.data.redis.core.RedisTemplate<String, String> redisTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final SandboxService sandboxService;
    private final SandboxFileService sandboxFileService;
    private final WorkspaceService workspaceService;
    
    // Keep track of agent run status in memory
    private final Map<String, AgentRunStatus> agentRunStatuses = new ConcurrentHashMap<>();
    private final Map<String, String> agentRunErrors = new ConcurrentHashMap<>();
    
    // Define the path to the todo.md file
    private static final String TODO_FILE_PATH = "/workspace/todo.md";
    
    /**
     * Execute an agent run.
     * 
     * @param agentRunId The agent run ID
     * @param threadId The thread ID
     * @param projectId The project ID
     * @param agentRunRequest The agent run request
     * @param instanceId The instance ID for Redis active run tracking
     * @return A CompletableFuture that completes when the agent run is finished
     */
    public CompletableFuture<Void> executeAgentRun(
            String agentRunId,
            String threadId,
            String projectId,
            AgentRunRequest agentRunRequest,
            String instanceId) {
            
        log.info("[AGENT_RUN {}] Starting for thread {}. Project: {}. Instance: {}",
                agentRunId, threadId, projectId, instanceId);
        
        // Set initial status
        agentRunStatuses.put(agentRunId, AgentRunStatus.RUNNING);
        
        // Define Redis keys
        final String responseListKey = "agent_run:" + agentRunId + ":responses";
        final String instanceActiveKey = "active_run:" + instanceId + ":" + agentRunId;
        
        // Create helper classes
        AgentExecutionHelper executionHelper = new AgentExecutionHelper(threadManager);
        AgentRedisHelper redisHelper = new AgentRedisHelper(
            new com.Nubian.ai.service.RedisService(redisTemplate, redisMessageListenerContainer),
            redisMessageListenerContainer, 
            objectMapper
        );
        AgentContextHelper contextHelper = new AgentContextHelper(contextManager, dbConnection, objectMapper);
        
        // Set up stop signal listener
        redisHelper.setupStopSignalListener(agentRunId, instanceId, 
                stopAgentRunId -> executionHelper.setStopSignal(stopAgentRunId, true));
        
        // Construct system prompt
        LlmMessage systemPrompt = AgentPromptHelper.constructSystemPrompt();
        
        // Resolve the model name
        String resolvedModel = agentConfig.resolveModelName(agentRunRequest.getModelName());
        log.info("[AGENT_RUN {}] Using model: {}", agentRunId, resolvedModel);
        
        // --- Tool Registration ---
        // This is a critical step: register all tools required for the agent's workflow.
        // These tools need to be instantiated with the correct projectId so they operate on the correct sandbox.
        // For now, we'll assume the existence of these tool classes and their dependencies.
        
        // Registering FileTool
        FileTool fileTool = new FileTool(projectId);
        fileTool.setSandboxService(sandboxService);
        fileTool.setSandboxFileService(sandboxFileService);
        fileTool.setThreadManager(threadManager);
        fileTool.setContextManager(contextManager);
        fileTool.setDbConnection(dbConnection);
        fileTool.setWorkspaceService(workspaceService);
        toolRegistry.registerTool(fileTool, null);
        log.info("[AGENT_RUN {}] Registered FileTool for project {}", agentRunId, projectId);
        
        // Registering MessageTool
        MessageTool messageTool = new MessageTool(objectMapper); // MODIFIED
        toolRegistry.registerTool(messageTool, null);
        log.info("[AGENT_RUN {}] Registered MessageTool", agentRunId);
        
        // Registering DataProviderTool
        DataProviderTool dataProviderTool = new DataProviderTool(objectMapper);
        toolRegistry.registerTool(dataProviderTool, null);
        log.info("[AGENT_RUN {}] Registered DataProviderTool", agentRunId);
        
        // Registering ProcessTool
        ProcessTool processTool = new ProcessTool(projectId);
        processTool.setSandboxService(sandboxService);
        processTool.setThreadManager(threadManager);
        processTool.setContextManager(contextManager);
        processTool.setDbConnection(dbConnection);
        processTool.setWorkspaceService(workspaceService);
        toolRegistry.registerTool(processTool, null);
        log.info("[AGENT_RUN {}] Registered ProcessTool for project {}", agentRunId, projectId);

        // Registering WorkspaceTool
        WorkspaceTool workspaceTool = new WorkspaceTool(projectId);
        workspaceTool.setSandboxService(sandboxService);
        workspaceTool.setSandboxFileService(sandboxFileService);
        workspaceTool.setThreadManager(threadManager);
        workspaceTool.setContextManager(contextManager);
        workspaceTool.setDbConnection(dbConnection);
        workspaceTool.setWorkspaceService(workspaceService);
        toolRegistry.registerTool(workspaceTool, null);
        log.info("[AGENT_RUN {}] Registered WorkspaceTool for project {}", agentRunId, projectId);

        // Registering BrowserTool
        BrowserTool browserTool = new BrowserTool(projectId);
        browserTool.setSandboxService(sandboxService);
        browserTool.setThreadManager(threadManager);
        browserTool.setContextManager(contextManager);
        browserTool.setDbConnection(dbConnection);
        browserTool.setWorkspaceService(workspaceService);
        toolRegistry.registerTool(browserTool, null);
        log.info("[AGENT_RUN {}] Registered BrowserTool for project {}", agentRunId, projectId);

        // Registering VisionTool
        VisionTool visionTool = new VisionTool(projectId);
        visionTool.setSandboxService(sandboxService);
        visionTool.setThreadManager(threadManager);
        visionTool.setContextManager(contextManager);
        visionTool.setDbConnection(dbConnection);
        visionTool.setWorkspaceService(workspaceService);
        toolRegistry.registerTool(visionTool, null);
        log.info("[AGENT_RUN {}] Registered VisionTool for project {}", agentRunId, projectId);

        // Registering DeployTool
        DeployTool deployTool = new DeployTool(projectId);
        deployTool.setSandboxService(sandboxService);
        deployTool.setThreadManager(threadManager);
        deployTool.setContextManager(contextManager);
        deployTool.setDbConnection(dbConnection);
        deployTool.setWorkspaceService(workspaceService);
        toolRegistry.registerTool(deployTool, null);
        log.info("[AGENT_RUN {}] Registered DeployTool for project {}", agentRunId, projectId);

        // Registering ExposeTool
        ExposeTool exposeTool = new ExposeTool(projectId);
        exposeTool.setSandboxService(sandboxService);
        exposeTool.setThreadManager(threadManager);
        exposeTool.setContextManager(contextManager);
        exposeTool.setDbConnection(dbConnection);
        exposeTool.setWorkspaceService(workspaceService);
        toolRegistry.registerTool(exposeTool, null);
        log.info("[AGENT_RUN {}] Registered ExposeTool for project {}", agentRunId, projectId);
        
        return CompletableFuture.runAsync(() -> {
            try {
                int iterationCount = 0;
                int maxIterations = agentConfig.getMaxIterations();
                boolean continueExecution = true;
                
                List<Message> allMessages = new ArrayList<>();
                
                // Capture start time for billing
                Instant llmCallStartTime = Instant.now();

                while (continueExecution && iterationCount < maxIterations) {
                    iterationCount++;
                    log.info("[AGENT_RUN {}] Iteration {}/{}", agentRunId, iterationCount, maxIterations);
                    
                    // --- Read todo.md ---
                    String todoContent = "";
                    try {
                        ToolResult readResult = fileTool.readFile(TODO_FILE_PATH).join();
                        if (readResult.isSuccess()) {
                            // Assuming the content is a String in the result map
                            Object contentObj = readResult.getOutput();
                            if (contentObj instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> contentMap = (Map<String, Object>) contentObj;
                                todoContent = (String) contentMap.get("content");
                                log.debug("[AGENT_RUN {}] Read todo.md:\n{}", agentRunId, todoContent);
                            } else if (contentObj instanceof String) {
                                todoContent = (String) contentObj;
                                log.debug("[AGENT_RUN {}] Read todo.md:\n{}", agentRunId, todoContent);
                            } else {
                                log.warn("[AGENT_RUN {}] Unexpected content type from readFile: {}", agentRunId, contentObj != null ? contentObj.getClass().getName() : "null");
                            }
                        } else {
                            log.warn("[AGENT_RUN {}] Failed to read todo.md: {}", agentRunId, readResult.getOutput());
                            // If todo.md doesn't exist, that's okay for the first iteration
                        }
                    } catch (Exception e) {
                        log.warn("[AGENT_RUN {}] Error reading todo.md: {}", agentRunId, e.getMessage());
                        // Continue if reading fails, maybe the file doesn't exist yet
                    }
                    
                    // --- Add todo.md content to context/prompt ---
                    // Add the todo.md content as a user message to the conversation history
                    if (!todoContent.isEmpty()) {
                        Map<String, Object> todoMessageContent = Map.of(
                            "role", "user",
                            "content", "Current todo.md:\n```\n" + todoContent + "\n```"
                        );
                        Message todoMessage = new Message(threadId, "user", todoMessageContent, false, null);
                        // Add the message to the thread history (asynchronously)
                        threadManager.addMessage(
                            threadId,
                            todoMessage.getType(),
                            todoMessage.getContent(),
                            todoMessage.isLlmMessage(),
                            todoMessage.getMetadata()
                        );
                    }
                    
                    // Check if we should stop
                    if (executionHelper.getStopSignal(agentRunId)) {
                        log.info("[AGENT_RUN {}] Stop signal received. Terminating.", agentRunId);
                        updateAgentRunStatus(agentRunId, AgentRunStatus.STOPPED, "Stopped by external signal", redisHelper);
                        break;
                    }
                    
                    // Check and summarize context if enabled
                    if (agentRunRequest.getEnableContextManager() != null && agentRunRequest.getEnableContextManager()) {
                        contextHelper.checkAndSummarizeContext(
                            threadId, 
                            resolvedModel, 
                            true,
                            agentRunRequest.getUserId(), // Pass userId
                            agentRunId, // Pass runId
                            llmCallStartTime // Pass startTime
                        ).join();
                    }
                    
                    // Construct temporary message with browser state and image context
                    LlmMessage temporaryMessage = contextHelper.constructTemporaryMessage(threadId).join();
                    
                    // Execute one iteration of the agent run loop with the real LLM
                    AgentLoopResult result = executionHelper.executeIteration(
                        agentRunId,
                        threadId,
                        projectId,
                        systemPrompt,
                        temporaryMessage,
                        resolvedModel,
                        agentRunRequest.getEnableThinking() != null ? agentRunRequest.getEnableThinking() : false,
                        agentRunRequest.getReasoningEffort(),
                        agentRunRequest.getStream() != null ? agentRunRequest.getStream() : true, // Default to streaming
                        agentRunRequest.getEnableContextManager() != null ? agentRunRequest.getEnableContextManager() : false,
                        agentRunRequest.getUserId(),
                        agentRunId,
                        llmCallStartTime
                    ).join();
                    
                    // Add messages to the result list
                    allMessages.addAll(result.getMessages());
                    
                    // Stream messages to Redis
                    for (Message message : result.getMessages()) {
                        redisHelper.publishMessageToRedisStream(agentRunId, message);
                    }
                    
                    // Update todo.md based on LLM output
                    for (Message message : result.getMessages()) {
                        if (updateTodoFileFromMessage(message, projectId, TODO_FILE_PATH, fileTool)) {
                            log.info("[AGENT_RUN {}] todo.md updated from LLM response", agentRunId);
                        }
                    }
                    
                    
                    // Process result
                    if (result.hasError()) {
                        log.error("[AGENT_RUN {}] Error in agent iteration", agentRunId);
                        updateAgentRunStatus(agentRunId, AgentRunStatus.FAILED, "Error during agent execution", redisHelper);
                        continueExecution = false;
                    } else if (result.getTerminatingTool() != null) {
                        log.info("[AGENT_RUN {}] Agent used terminating tool: {}", agentRunId, result.getTerminatingTool());
                        
                        if ("complete".equals(result.getTerminatingTool())) {
                            updateAgentRunStatus(agentRunId, AgentRunStatus.COMPLETED, null, redisHelper);
                        } else { // ask, web-browser-takeover
                            // Store as STOPPED but with a special message that indicates pause
                            updateAgentRunStatus(agentRunId, AgentRunStatus.STOPPED, 
                                    "Awaiting user input for " + result.getTerminatingTool(), redisHelper);
                        }
                        
                        continueExecution = false;
                    } else if (!result.shouldContinue()) {
                        log.info("[AGENT_RUN {}] Agent indicated it should not continue", agentRunId);
                        updateAgentRunStatus(agentRunId, AgentRunStatus.COMPLETED, null, redisHelper);
                        continueExecution = false;
                    }
                    
                    // Brief pause to prevent tight loops
                    if (continueExecution) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            log.warn("[AGENT_RUN {}] Iteration sleep interrupted", agentRunId);
                            Thread.currentThread().interrupt();
                            updateAgentRunStatus(agentRunId, AgentRunStatus.STOPPED, "Interrupted", redisHelper);
                            continueExecution = false;
                        }
                    }
                }
                
                // If we reached max iterations, mark as completed
                if (iterationCount >= maxIterations && agentRunStatuses.get(agentRunId) == AgentRunStatus.RUNNING) {
                    log.warn("[AGENT_RUN {}] Reached maximum iterations ({}). Marking as completed.", 
                            agentRunId, maxIterations);
                    updateAgentRunStatus(agentRunId, AgentRunStatus.COMPLETED, "Reached maximum iterations", redisHelper);
                }
                
                // Clean up Redis resources
                redisHelper.cleanupRedisForRun(agentRunId, instanceId, responseListKey);
                
                log.info("[AGENT_RUN {}] Execution finished", agentRunId);
            } catch (Exception e) {
                log.error("[AGENT_RUN {}] Unexpected error: {}", agentRunId, e.getMessage(), e);
                updateAgentRunStatus(agentRunId, AgentRunStatus.FAILED, e.getMessage(), redisHelper);
            }
        });
    }
    
    /**
     * Update the status of an agent run.
     * 
     * @param agentRunId The agent run ID
     * @param status The new status
     * @param errorMessage The error message (if any)
     * @param redisHelper The redis helper for publishing status updates
     */
    private void updateAgentRunStatus(
            String agentRunId, 
            AgentRunStatus status, 
            String errorMessage,
            AgentRedisHelper redisHelper) {
            
        log.info("[AGENT_RUN {}] Updating status to {}", agentRunId, status);
        
        // Persist status to the database
        try {
            Instant completedAt = null;
            if (status == AgentRunStatus.COMPLETED || status == AgentRunStatus.STOPPED || status == AgentRunStatus.FAILED) { // ADDED FAILED
                completedAt = Instant.now();
            }

            Map<String, Object> values = new java.util.HashMap<>();
            values.put("status", status.name());
            values.put("error_message", errorMessage);
            values.put("completed_at", completedAt);

            Map<String, Object> conditions = new java.util.HashMap<>();
            conditions.put("id", agentRunId);

            dbConnection.update("agent_runs", values, conditions).join(); // Using join to ensure completion for logging
            log.debug("[AGENT_RUN {}] Status persisted to database", agentRunId);
        } catch (Exception e) {
            log.error("[AGENT_RUN {}] Failed to persist status to database: {}", agentRunId, e.getMessage(), e);
            // Continue execution even if database update fails
        }
        
        // Update status in memory
        agentRunStatuses.put(agentRunId, status);
        
        if (errorMessage != null) {
            agentRunErrors.put(agentRunId, errorMessage);
        } else if (status != AgentRunStatus.FAILED && agentRunErrors.containsKey(agentRunId)) {
            agentRunErrors.remove(agentRunId);
        }
        
        // Update status in Redis
        redisHelper.updateAgentRunStatus(
            agentRunId, 
            status, 
            errorMessage, 
            redisHelper.getResponsesFromRedis(agentRunId)
        );
    }
    
    /**
     * Get the status of an agent run.
     * 
     * @param agentRunId The agent run ID
     * @return The current status
     */
    public AgentRunStatus getAgentRunStatus(String agentRunId) {
        return agentRunStatuses.getOrDefault(agentRunId, null);
    }
    
    /**
     * Get the error message for a failed agent run.
     * 
     * @param agentRunId The agent run ID
     * @return The error message
     */
    public String getAgentRunError(String agentRunId) {
        return agentRunErrors.get(agentRunId);
    }
    
    /**
     * Get the sandbox service.
     * 
     * @return The sandbox service
     */
    public SandboxService getSandboxService() {
        return sandboxService;
    }
    
    /**
     * Get the sandbox file service.
     * 
     * @return The sandbox file service
     */
    public SandboxFileService getSandboxFileService() {
        return sandboxFileService;
    }
    
    /**
     * Get the workspace service.
     * 
     * @return The workspace service
     */
    public WorkspaceService getWorkspaceService() {
        return workspaceService;
    }
    
    /**
     * Update the todo.md file based on the content of a message.
     * 
     * @param message The message to check for todo updates
     * @param projectId The project ID
     * @param todoPath The path to the todo.md file
     * @param fileTool The FileTool instance to use for writing the file
     * @return true if the todo file was updated, false otherwise
     */
    private boolean updateTodoFileFromMessage(Message message, String projectId, String todoPath, FileTool fileTool) {
        if (!"assistant".equals(message.getType())) {
            return false;
        }
        
        Object contentObj = message.getContent();
        if (!(contentObj instanceof Map)) {
            return false;
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> contentMap = (Map<String, Object>) contentObj;
        String textContent = (String) contentMap.get("content");
        
        if (textContent == null) {
            return false;
        }
        
        // Check for todo updates in <todo_update> tags
        if (textContent.contains("<todo_update>")) {
            try {
                // Extract content between <todo_update> and </todo_update>
                int startIndex = textContent.indexOf("<todo_update>") + "<todo_update>".length();
                int endIndex = textContent.indexOf("</todo_update>", startIndex);
                if (endIndex != -1) {
                    String updatedTodoContent = textContent.substring(startIndex, endIndex).trim();
                    log.info("Detected todo.md update. Writing to file.");
                    
                    // Use the FileTool to write the updated content to the todo.md file
                    fileTool.writeFile(todoPath, updatedTodoContent, false).join();
                    return true;
                }
            } catch (Exception e) {
                log.error("Error parsing or writing todo.md update: {}", e.getMessage(), e);
            }
        }
        
        return false;
    }
}
