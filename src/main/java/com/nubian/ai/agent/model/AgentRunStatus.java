package com.nubian.ai.agent.model;

/**
 * Enumeration of possible agent run statuses.
 */
public enum AgentRunStatus {
    /**
     * Agent run is currently in progress.
     */
    RUNNING,
    
    /**
     * Agent run has completed successfully.
     */
    COMPLETED,
    
    /**
     * Agent run was manually stopped by the user.
     */
    STOPPED,
    
    /**
     * Agent run failed due to an error.
     */
    FAILED
}
