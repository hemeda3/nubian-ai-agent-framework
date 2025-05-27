package com.Nubian.ai.agent.model;

import lombok.Data;

/**
 * Request model for starting an agent run.
 */
@Data
public class AgentRunRequest {
    /**
     * The model name to use for the agent.
     */
    private String modelName;
    
    /**
     * Whether to enable thinking mode.
     */
    private Boolean enableThinking;
    
    /**
     * The reasoning effort level (low, medium, high).
     */
    private String reasoningEffort;
    
    /**
     * Whether to stream the response.
     */
    private Boolean stream;
    
    /**
     * Whether to enable the context manager.
     */
    private Boolean enableContextManager;
    
    /**
     * The initial prompt or message from the user.
     */
    private String initialPrompt;
    
    /**
     * The ID of the user initiating the agent run.
     */
    private String userId;
}
