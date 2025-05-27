package com.Nubian.ai.agent.model;

import lombok.Data;

/**
 * Response model for agent run operations.
 */
@Data
public class AgentRunResponse {
    /**
     * The agent run ID.
     */
    private String agentRunId;
    
    /**
     * The status of the agent run.
     */
    private AgentRunStatus status;
    
    /**
     * Error message if the agent run failed.
     */
    private String errorMessage;
    
    /**
     * The ID of the thread this agent run is associated with.
     */
    private String threadId;
    
    /**
     * Create a response for a running agent.
     * 
     * @param agentRunId The agent run ID
     * @return The response
     */
    public static AgentRunResponse running(String agentRunId) {
        AgentRunResponse response = new AgentRunResponse();
        response.setAgentRunId(agentRunId);
        response.setStatus(AgentRunStatus.RUNNING);
        return response;
    }
    
    /**
     * Create a response for a completed agent.
     * 
     * @param agentRunId The agent run ID
     * @return The response
     */
    public static AgentRunResponse completed(String agentRunId) {
        AgentRunResponse response = new AgentRunResponse();
        response.setAgentRunId(agentRunId);
        response.setStatus(AgentRunStatus.COMPLETED);
        return response;
    }
    
    /**
     * Create a response for a stopped agent.
     * 
     * @param agentRunId The agent run ID
     * @return The response
     */
    public static AgentRunResponse stopped(String agentRunId) {
        AgentRunResponse response = new AgentRunResponse();
        response.setAgentRunId(agentRunId);
        response.setStatus(AgentRunStatus.STOPPED);
        return response;
    }
    
    /**
     * Create a response for a failed agent.
     * 
     * @param agentRunId The agent run ID
     * @param errorMessage The error message
     * @return The response
     */
    public static AgentRunResponse failed(String agentRunId, String errorMessage) {
        AgentRunResponse response = new AgentRunResponse();
        response.setAgentRunId(agentRunId);
        response.setStatus(AgentRunStatus.FAILED);
        response.setErrorMessage(errorMessage);
        return response;
    }
}
