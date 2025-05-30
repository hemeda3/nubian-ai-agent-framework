package com.nubian.ai.agent.service;

import com.nubian.ai.agent.config.AgentConfig;
import com.nubian.ai.agent.model.AgentRunRequest;
import com.nubian.ai.agent.model.AgentRunStatus;
import com.nubian.ai.agentpress.service.ContextManager;
import com.nubian.ai.agentpress.service.ThreadManager;
import com.nubian.ai.agentpress.service.ToolRegistry;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Manages agent runs, providing functionality to start, stop, and track agent executions.
 * 
 * This service acts as a bridge between the agent controller and the core AgentPress components,
 * handling the lifecycle of agent runs and maintaining their state.
 * 
 * This class delegates to the new modular implementation in the manager package.
 */
@Service
@Slf4j
public class AgentRunManager {

    private final com.nubian.ai.agent.service.manager.AgentRunManager delegateManager;

    @Autowired
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
        
        // Create delegate manager with all the same dependencies
        this.delegateManager = new com.nubian.ai.agent.service.manager.AgentRunManager(
            threadManager, contextManager, toolRegistry, agentConfig, agentRunnerService,
            objectMapper, redisTemplate, redisMessageListenerContainer, 
            dbConnection, accountService, projectService);
    }

    /**
     * Start a new agent run.
     * 
     * @param agentRunId The unique ID for this agent run
     * @param request The agent run request containing configuration
     */
    public void startAgentRun(String agentRunId, AgentRunRequest request) {
        delegateManager.startAgentRun(agentRunId, request);
    }

    /**
     * Get the status of an agent run.
     * 
     * @param agentRunId The agent run ID
     * @return The current status
     */
    public AgentRunStatus getAgentRunStatus(String agentRunId) {
        return delegateManager.getAgentRunStatus(agentRunId);
    }

    /**
     * Get the error message for a failed agent run.
     * 
     * @param agentRunId The agent run ID
     * @return The error message, or null if not failed
     */
    public String getAgentRunError(String agentRunId) {
        return delegateManager.getAgentRunError(agentRunId);
    }
    
    /**
     * Set the status of an agent run with an optional error message.
     * 
     * @param agentRunId The agent run ID
     * @param status The new status
     * @param errorMessage The error message (null if no error)
     */
    public void setAgentRunStatus(String agentRunId, AgentRunStatus status, String errorMessage) {
        delegateManager.setAgentRunStatus(agentRunId, status, errorMessage);
    }

    /**
     * Stop an agent run.
     * 
     * @param agentRunId The agent run ID
     */
    public void stopAgentRun(String agentRunId) {
        delegateManager.stopAgentRun(agentRunId);
    }

    /**
     * Get the thread ID for an agent run.
     * 
     * @param agentRunId The agent run ID
     * @return The thread ID
     */
    public String getThreadIdForAgentRun(String agentRunId) {
        return delegateManager.getThreadIdForAgentRun(agentRunId);
    }
}
