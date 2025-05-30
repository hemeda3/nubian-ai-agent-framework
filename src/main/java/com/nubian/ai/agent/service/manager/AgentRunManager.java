package com.nubian.ai.agent.service.manager;

import com.nubian.ai.agent.config.AgentConfig;
import com.nubian.ai.agent.model.AgentRunRequest;
import com.nubian.ai.agent.model.AgentRunStatus;
import com.nubian.ai.agentpress.service.ContextManager;
import com.nubian.ai.agentpress.service.ThreadManager;
import com.nubian.ai.agentpress.service.ToolRegistry;
import com.nubian.ai.agent.service.AgentRunnerService;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Manages agent runs, providing functionality to start, stop, and track agent executions.
 * 
 * This class acts as a bridge between the agent controller and the core AgentPress components,
 * handling the lifecycle of agent runs and maintaining their state.
 * 
 * Note: This is not a Spring bean but a helper class instantiated directly by the
 * service layer AgentRunManager.
 */
@Slf4j
public class AgentRunManager {

    private final ThreadManager threadManager;
    private final ContextManager contextManager;
    private final ToolRegistry toolRegistry;
    private final AgentConfig agentConfig;
    private final AgentRunnerService agentRunnerService;
    private final ObjectMapper objectMapper;
    private final org.springframework.data.redis.core.RedisTemplate<String, String> redisTemplate;
    private final org.springframework.data.redis.listener.RedisMessageListenerContainer redisMessageListenerContainer;
    private final com.nubian.ai.agentpress.service.DBConnection dbConnection;
    private final com.nubian.ai.agentpress.service.AccountService accountService;
    private final com.nubian.ai.agentpress.service.ProjectService projectService;
    
    // Maps to track agent runs
    private final Map<String, AgentRunStatus> agentRunStatuses = new ConcurrentHashMap<>();
    private final Map<String, String> agentRunErrors = new ConcurrentHashMap<>();
    private final Map<String, String> agentRunToThreadId = new ConcurrentHashMap<>();
    private final Map<String, Future<?>> agentRunFutures = new ConcurrentHashMap<>();
    
    // Thread pool for executing agent runs
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    // Component managers
    private final ToolRegistrationManager toolRegistrationManager;
    private final FileOperationManager fileOperationManager;
    private final StatusUpdateManager statusUpdateManager;
    private final ProjectResolver projectResolver;
    private final ExecutionManager executionManager;

    public AgentRunManager(ThreadManager threadManager, 
                          ContextManager contextManager,
                          ToolRegistry toolRegistry,
                          AgentConfig agentConfig,
                          AgentRunnerService agentRunnerService,
                          ObjectMapper objectMapper,
                          org.springframework.data.redis.core.RedisTemplate<String, String> redisTemplate,
                          org.springframework.data.redis.listener.RedisMessageListenerContainer redisMessageListenerContainer,
                          com.nubian.ai.agentpress.service.DBConnection dbConnection,
                          com.nubian.ai.agentpress.service.AccountService accountService,
                          com.nubian.ai.agentpress.service.ProjectService projectService) {
        this.threadManager = threadManager;
        this.contextManager = contextManager;
        this.toolRegistry = toolRegistry;
        this.agentConfig = agentConfig;
        this.agentRunnerService = agentRunnerService;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.redisMessageListenerContainer = redisMessageListenerContainer;
        this.dbConnection = dbConnection;
        this.accountService = accountService;
        this.projectService = projectService;
        
        // Initialize component managers
        this.toolRegistrationManager = new ToolRegistrationManager(
            toolRegistry, agentRunnerService, threadManager, contextManager, dbConnection);
            
        this.fileOperationManager = new FileOperationManager(
            agentRunnerService, threadManager, contextManager, dbConnection);
            
        this.statusUpdateManager = new StatusUpdateManager(
            dbConnection, agentRunStatuses, agentRunErrors);
            
        this.projectResolver = new ProjectResolver(
            dbConnection, accountService, projectService, threadManager);
            
        this.executionManager = new ExecutionManager(
            agentConfig, threadManager, contextManager, dbConnection, objectMapper,
            redisTemplate, redisMessageListenerContainer, toolRegistrationManager,
            fileOperationManager, statusUpdateManager, projectResolver);
    }

    /**
     * Start a new agent run.
     * 
     * @param agentRunId The unique ID for this agent run
     * @param request The agent run request containing configuration
     */
    public void startAgentRun(String agentRunId, AgentRunRequest request) {
        log.info("Starting agent run: {}", agentRunId);
        
        // Generate a unique thread ID for this agent run
        String threadId = UUID.randomUUID().toString();
        
        // Store the mapping between agent run and thread
        agentRunToThreadId.put(agentRunId, threadId);
        
        // Set initial status
        agentRunStatuses.put(agentRunId, AgentRunStatus.RUNNING);
        
        // Submit the agent run task to the executor service
        Future<?> future = executorService.submit(() -> {
            try {
                executionManager.executeAgentRun(agentRunId, threadId, request,
                        agentRunStatuses, agentRunErrors);
            } catch (Exception e) {
                log.error("Error executing agent run {}: {}", agentRunId, e.getMessage(), e);
                agentRunStatuses.put(agentRunId, AgentRunStatus.FAILED);
                agentRunErrors.put(agentRunId, e.getMessage());
            }
        });
        
        // Store the future for potential cancellation
        agentRunFutures.put(agentRunId, future);
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
     * @return The error message, or null if not failed
     */
    public String getAgentRunError(String agentRunId) {
        return agentRunErrors.get(agentRunId);
    }
    
    /**
     * Set the status of an agent run with an optional error message.
     * 
     * @param agentRunId The agent run ID
     * @param status The new status
     * @param errorMessage The error message (null if no error)
     */
    public void setAgentRunStatus(String agentRunId, AgentRunStatus status, String errorMessage) {
        statusUpdateManager.setAgentRunStatus(agentRunId, status, errorMessage, agentRunStatuses, agentRunErrors);
    }

    /**
     * Stop an agent run.
     * 
     * @param agentRunId The agent run ID
     */
    public void stopAgentRun(String agentRunId) {
        log.info("Stopping agent run: {}", agentRunId);
        
        Future<?> future = agentRunFutures.get(agentRunId);
        if (future != null) {
            future.cancel(true);
        }
        
        agentRunStatuses.put(agentRunId, AgentRunStatus.STOPPED);
    }

    /**
     * Get the thread ID for an agent run.
     * 
     * @param agentRunId The agent run ID
     * @return The thread ID
     */
    public String getThreadIdForAgentRun(String agentRunId) {
        return agentRunToThreadId.get(agentRunId);
    }
}
