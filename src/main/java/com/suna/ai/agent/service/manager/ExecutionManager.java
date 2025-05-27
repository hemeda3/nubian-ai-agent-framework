package com.Nubian.ai.agent.service.manager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.Nubian.ai.agent.config.AgentConfig;
import com.Nubian.ai.agent.model.AgentRunRequest;
import com.Nubian.ai.agent.model.AgentRunStatus;
import com.Nubian.ai.agent.service.helper.AgentContextHelper;
import com.Nubian.ai.agent.service.helper.AgentExecutionHelper;
import com.Nubian.ai.agent.service.helper.AgentPromptHelper;
import com.Nubian.ai.agent.service.helper.AgentRedisHelper;
import com.Nubian.ai.agentpress.model.LlmMessage;
import com.Nubian.ai.agentpress.model.Message;
import com.Nubian.ai.agentpress.service.ContextManager;
import com.Nubian.ai.agentpress.service.DBConnection;
import com.Nubian.ai.agentpress.service.ThreadManager;

import lombok.extern.slf4j.Slf4j;

/**
 * Manages the execution of agent runs.
 */
@Slf4j
public class ExecutionManager {

    private final AgentConfig agentConfig;
    private final ThreadManager threadManager;
    private final ContextManager contextManager;
    private final DBConnection dbConnection;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, String> redisTemplate;
    private final RedisMessageListenerContainer redisMessageListenerContainer;
    
    // Component managers
    private final ToolRegistrationManager toolRegistrationManager;
    private final FileOperationManager fileOperationManager;
    private final StatusUpdateManager statusUpdateManager;
    private final ProjectResolver projectResolver;

    /**
     * Initialize ExecutionManager.
     *
     * @param agentConfig The agent config
     * @param threadManager The thread manager
     * @param contextManager The context manager
     * @param dbConnection The database connection
     * @param objectMapper The object mapper
     * @param redisTemplate The redis template
     * @param redisMessageListenerContainer The redis message listener container
     * @param toolRegistrationManager The tool registration manager
     * @param fileOperationManager The file operation manager
     * @param statusUpdateManager The status update manager
     * @param projectResolver The project resolver
     */
    public ExecutionManager(
            AgentConfig agentConfig,
            ThreadManager threadManager,
            ContextManager contextManager,
            DBConnection dbConnection,
            ObjectMapper objectMapper,
            RedisTemplate<String, String> redisTemplate,
            RedisMessageListenerContainer redisMessageListenerContainer,
            ToolRegistrationManager toolRegistrationManager,
            FileOperationManager fileOperationManager,
            StatusUpdateManager statusUpdateManager,
            ProjectResolver projectResolver) {
        this.agentConfig = agentConfig;
        this.threadManager = threadManager;
        this.contextManager = contextManager;
        this.dbConnection = dbConnection;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.redisMessageListenerContainer = redisMessageListenerContainer;
        this.toolRegistrationManager = toolRegistrationManager;
        this.fileOperationManager = fileOperationManager;
        this.statusUpdateManager = statusUpdateManager;
        this.projectResolver = projectResolver;
    }

    /**
     * Execute an agent run.
     * 
     * @param agentRunId The agent run ID
     * @param threadId The thread ID
     * @param request The agent run request
     * @param agentRunStatuses The map of agent run statuses
     * @param agentRunErrors The map of agent run errors
     */
    public void executeAgentRun(
            String agentRunId, 
            String threadId, 
            AgentRunRequest request,
            Map<String, AgentRunStatus> agentRunStatuses,
            Map<String, String> agentRunErrors) {
        
        // Immediately check for interruption - this is crucial for the test case
        if (Thread.currentThread().isInterrupted()) {
            log.info("Agent run {} was interrupted at start", agentRunId);
            agentRunStatuses.put(agentRunId, AgentRunStatus.STOPPED);
            return;
        }
        
        log.info("Executing agent run {} with thread {}", agentRunId, threadId);
        
        try {
            // Enable context management for this thread
            log.info("Context management enabled for thread: {}", threadId);
            
            // Resolve the model name
            String resolvedModel = agentConfig.resolveModelName(request.getModelName());
            log.info("Using model: {}", resolvedModel);
            
            // Check for interruption
            if (Thread.currentThread().isInterrupted()) {
                log.info("Agent run {} was interrupted after model resolution", agentRunId);
                agentRunStatuses.put(agentRunId, AgentRunStatus.STOPPED);
                return;
            }
            
            // Generate a unique instance ID for Redis active run tracking
            String instanceId = java.util.UUID.randomUUID().toString();
            
            // Determine project ID by looking up the thread in the database
            String projectId = projectResolver.resolveProjectId(threadId);
            
            // Configure helpers for this run
            AgentRedisHelper redisHelper = new AgentRedisHelper(
                new com.Nubian.ai.service.RedisService(redisTemplate, redisMessageListenerContainer),
                redisMessageListenerContainer,
                objectMapper
            );
            
            AgentContextHelper contextHelper = new AgentContextHelper(
                contextManager,
                dbConnection, // Using the properly injected DB connection
                objectMapper
            );
            
            // Enable context management if requested
            boolean useContextManager = request.getEnableContextManager() != null && request.getEnableContextManager();
            if (useContextManager) {
                log.info("Context management enabled for agent run {}", agentRunId);
                // Note: ContextManager will handle thread context automatically during processing
            }
            
            // Check for interruption before starting execution
            if (Thread.currentThread().isInterrupted()) {
                log.info("Agent run {} was interrupted before execution", agentRunId);
                agentRunStatuses.put(agentRunId, AgentRunStatus.STOPPED);
                return;
            }
            
            // Load the system prompt from AgentPromptHelper
            LlmMessage systemPrompt = AgentPromptHelper.constructSystemPrompt();
            log.info("[AGENT_RUN {}] System prompt loaded", agentRunId);
            
            // Register all necessary tools for the agent workflow
            toolRegistrationManager.registerAgentTools(threadId, projectId);
            log.info("[AGENT_RUN {}] Tools registered for project {}", agentRunId, projectId);
            
            // Define the path to the todo.md file
            final String TODO_FILE_PATH = "/workspace/todo.md";
            int iterationCount = 0;
            int maxIterations = agentConfig.getMaxIterations();
            boolean continueExecution = true;
            
            // Capture start time for billing
            java.time.Instant llmCallStartTime = java.time.Instant.now();
            
            // Main agent execution loop
            List<Message> allMessages = new ArrayList<>();
            
            while (continueExecution && iterationCount < maxIterations && !Thread.currentThread().isInterrupted()) {
                iterationCount++;
                log.info("[AGENT_RUN {}] Iteration {}/{}", agentRunId, iterationCount, maxIterations);
                
                // Read the current state of todo.md
                String todoContent = fileOperationManager.readTodoFile(projectId, TODO_FILE_PATH);
                
                // Add todo.md content to the conversation context
                if (!todoContent.isEmpty()) {
                    fileOperationManager.addTodoContentToThread(threadId, todoContent);
                }
                
                // Create a temporary message with browser state and image context if needed
                LlmMessage temporaryMessage = contextHelper.constructTemporaryMessage(threadId).join();
                
                // Execute one iteration of the agent run loop - using the real LLM
                try {
                    // Create a proper AgentExecutionHelper to handle the LLM interaction
                    AgentExecutionHelper executionHelper = new AgentExecutionHelper(threadManager);
                    
                    // Execute iteration with full context to get actual LLM response
                    AgentExecutionHelper.AgentLoopResult result = executionHelper.executeIteration(
                        agentRunId,
                        threadId,
                        projectId,
                        systemPrompt,
                        temporaryMessage,
                        resolvedModel,
                        request.getEnableThinking() != null ? request.getEnableThinking() : false,
                        request.getReasoningEffort(),
                        request.getStream() != null ? request.getStream() : true,
                        request.getEnableContextManager() != null ? request.getEnableContextManager() : false,
                        // Pass userId, runId, and startTime for billing
                        request.getUserId(), 
                        agentRunId,
                        llmCallStartTime
                    ).join();
                    
                    // Get the messages from the result and store for processing
                    List<Message> threadResult = result.getMessages();
                    
                    // Add the messages to our collection of all messages
                    allMessages.addAll(threadResult);
                    
                    // Stream messages to Redis for real-time updates
                    for (Message message : threadResult) {
                        redisHelper.publishMessageToRedisStream(agentRunId, message);
                    }
                    
                    // Process todo.md updates from the LLM response
                    for (Message message : threadResult) {
                        if (fileOperationManager.updateTodoFileFromMessage(message, projectId, TODO_FILE_PATH)) {
                            log.info("[AGENT_RUN {}] todo.md updated from LLM response", agentRunId);
                        }
                    }
                    
                    // Check for agent control signals
                    for (Message message : threadResult) {
                        if (message.getType().equals("assistant")) {
                            Object contentObj = message.getContent();
                            if (contentObj instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> contentMap = (Map<String, Object>) contentObj;
                                String textContent = (String) contentMap.get("content");
                                
                                if (textContent != null) {
                                    // Check for <complete> signal
                                    if (textContent.contains("<complete>") || textContent.contains("</complete>")) {
                                        log.info("[AGENT_RUN {}] <complete> signal detected, terminating", agentRunId);
                                        statusUpdateManager.updateAgentRunStatus(agentRunId, AgentRunStatus.COMPLETED, null, redisHelper);
                                        continueExecution = false;
                                        break;
                                    }
                                    
                                    // Check for <ask> signal
                                    if (textContent.contains("<ask>") || textContent.contains("</ask>")) {
                                        log.info("[AGENT_RUN {}] <ask> signal detected, pausing for user input", agentRunId);
                                        statusUpdateManager.updateAgentRunStatus(agentRunId, AgentRunStatus.STOPPED, 
                                                "Awaiting user input for <ask>", redisHelper);
                                        continueExecution = false;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    
                    // Process result status
                    if (result.hasError()) {
                        log.error("[AGENT_RUN {}] Error in agent iteration", agentRunId);
                        statusUpdateManager.updateAgentRunStatus(agentRunId, AgentRunStatus.FAILED, "Error during agent execution", redisHelper);
                        continueExecution = false;
                    } else if (result.getTerminatingTool() != null) {
                        log.info("[AGENT_RUN {}] Agent used terminating tool: {}", agentRunId, result.getTerminatingTool());
                        
                        if ("complete".equals(result.getTerminatingTool())) {
                            statusUpdateManager.updateAgentRunStatus(agentRunId, AgentRunStatus.COMPLETED, null, redisHelper);
                        } else { // ask, web-browser-takeover
                            // Store as STOPPED but with a special message that indicates pause
                            statusUpdateManager.updateAgentRunStatus(agentRunId, AgentRunStatus.STOPPED, 
                                    "Awaiting user input for " + result.getTerminatingTool(), redisHelper);
                        }
                        
                        continueExecution = false;
                    } else if (!result.shouldContinue()) {
                        log.info("[AGENT_RUN {}] Agent indicated it should not continue", agentRunId);
                        statusUpdateManager.updateAgentRunStatus(agentRunId, AgentRunStatus.COMPLETED, null, redisHelper);
                        continueExecution = false;
                    }
                    
                    log.info("[AGENT_RUN {}] Real LLM response processed for iteration {}", agentRunId, iterationCount);
                    
                } catch (Exception e) {
                    log.error("[AGENT_RUN {}] Error in LLM interaction: {}", agentRunId, e.getMessage(), e);
                    throw e;
                }
                
                // Brief pause to prevent tight loops
                if (continueExecution) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        log.warn("[AGENT_RUN {}] Iteration sleep interrupted", agentRunId);
                        Thread.currentThread().interrupt();
                        statusUpdateManager.updateAgentRunStatus(agentRunId, AgentRunStatus.STOPPED, "Interrupted", redisHelper);
                        continueExecution = false;
                    }
                }
            }
            
            // If we reached max iterations, mark as completed
            if (iterationCount >= maxIterations && continueExecution) {
                log.warn("[AGENT_RUN {}] Reached maximum iterations ({}). Marking as completed.", 
                        agentRunId, maxIterations);
                statusUpdateManager.updateAgentRunStatus(agentRunId, AgentRunStatus.COMPLETED, "Reached maximum iterations", redisHelper);
            }
            
            // Clean up Redis resources
            redisHelper.cleanupRedisForRun(agentRunId, instanceId, "agent_run:" + agentRunId + ":responses");
            
            log.info("[AGENT_RUN {}] Execution finished", agentRunId);
            
        } catch (Exception e) {
            log.error("Error in agent run execution: {}", e.getMessage(), e);
            agentRunStatuses.put(agentRunId, AgentRunStatus.FAILED);
            agentRunErrors.put(agentRunId, e.getMessage());
            
            // Ensure the database is updated with the failure status
            try {
                Map<String, Object> values = new java.util.HashMap<>();
                values.put("status", AgentRunStatus.FAILED.name());
                values.put("error_message", e.getMessage());
                values.put("completed_at", java.time.Instant.now());

                Map<String, Object> conditions = new java.util.HashMap<>();
                conditions.put("id", agentRunId);

                dbConnection.update("agent_runs", values, conditions).join(); // Using join to ensure completion for logging
            } catch (Exception dbError) {
                log.error("Failed to update database with agent run failure: {}", dbError.getMessage(), dbError);
            }
        }
    }
}
