package com.Nubian.ai.agentpress.sandbox.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Model for Daytona process session data returned from the API.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DaytonaProcessSession {
    /**
     * The session ID.
     */
    private String id;
    
    /**
     * The session status.
     */
    private String status;
    
    /**
     * The workspace ID associated with this session.
     */
    private String workspaceId;
    
    /**
     * The creation time of the session.
     */
    private LocalDateTime createdAt;
    
    /**
     * The last activity time of the session.
     */
    private LocalDateTime lastActivityAt;
}
