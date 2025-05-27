package com.Nubian.ai.agent.service.manager;

import java.util.Map;

import com.Nubian.ai.agent.model.AgentRunStatus;
import com.Nubian.ai.agent.service.helper.AgentRedisHelper;
import com.Nubian.ai.agentpress.service.DBConnection;

import lombok.extern.slf4j.Slf4j;

/**
 * Manages status updates for agent runs.
 */
@Slf4j
public class StatusUpdateManager {

    private final DBConnection dbConnection;
    private final Map<String, AgentRunStatus> agentRunStatuses;
    private final Map<String, String> agentRunErrors;

    /**
     * Initialize StatusUpdateManager.
     *
     * @param dbConnection The database connection
     * @param agentRunStatuses The agent run statuses map
     * @param agentRunErrors The agent run errors map
     */
    public StatusUpdateManager(
            DBConnection dbConnection,
            Map<String, AgentRunStatus> agentRunStatuses,
            Map<String, String> agentRunErrors) {
        this.dbConnection = dbConnection;
        this.agentRunStatuses = agentRunStatuses;
        this.agentRunErrors = agentRunErrors;
    }

    /**
     * Update the status of an agent run.
     * 
     * @param agentRunId The agent run ID
     * @param status The new status
     * @param errorMessage The error message (if any)
     * @param redisHelper The redis helper for publishing status updates
     */
    public void updateAgentRunStatus(
            String agentRunId, 
            AgentRunStatus status, 
            String errorMessage,
            AgentRedisHelper redisHelper) {
            
        log.info("[AGENT_RUN {}] Updating status to {}", agentRunId, status);
        
        // Persist status to the database
        try {
            java.time.Instant completedAt = null;
            if (status == AgentRunStatus.COMPLETED || status == AgentRunStatus.STOPPED || status == AgentRunStatus.FAILED) { // ADDED FAILED
                completedAt = java.time.Instant.now();
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
        
        // Update status in Redis if helper is available
        if (redisHelper != null) {
            redisHelper.updateAgentRunStatus(
                agentRunId, 
                status, 
                errorMessage, 
                redisHelper.getResponsesFromRedis(agentRunId)
            );
        }
    }
    
    /**
     * Set the status of an agent run with an optional error message.
     * 
     * @param agentRunId The agent run ID
     * @param status The new status
     * @param errorMessage The error message (null if no error)
     * @param agentRunStatuses The agent run statuses map
     * @param agentRunErrors The agent run errors map
     */
    public void setAgentRunStatus(
            String agentRunId, 
            AgentRunStatus status, 
            String errorMessage,
            Map<String, AgentRunStatus> agentRunStatuses,
            Map<String, String> agentRunErrors) {
        log.info("Setting agent run {} status to {}", agentRunId, status);
        
        agentRunStatuses.put(agentRunId, status);
        
        if (errorMessage != null) {
            agentRunErrors.put(agentRunId, errorMessage);
            log.info("Agent run {} error: {}", agentRunId, errorMessage);
        } else if (status != AgentRunStatus.FAILED && agentRunErrors.containsKey(agentRunId)) {
            // Clear any error message if the status is not FAILED
            agentRunErrors.remove(agentRunId);
        }
    }
}
