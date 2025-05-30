package com.nubian.ai.agentpress.sandbox.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Model for Daytona command execution data.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DaytonaCommandExecution {
    /**
     * The command ID.
     */
    private String id;
    
    /**
     * The session ID associated with this command.
     */
    private String sessionId;
    
    /**
     * The command that was executed.
     */
    private String command;
    
    /**
     * The exit code returned by the command.
     */
    private Integer exitCode;
    
    /**
     * Standard output from the command.
     */
    private String stdout;
    
    /**
     * Standard error from the command.
     */
    private String stderr;
    
    /**
     * Whether the command is still running.
     */
    private boolean isRunning;
    
    /**
     * The working directory where the command was executed.
     */
    private String workingDirectory;
    
    /**
     * The creation time of the command execution.
     */
    private LocalDateTime createdAt;
    
    /**
     * The completion time of the command execution.
     */
    private LocalDateTime completedAt;
    
    /**
     * Request model for command execution.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExecuteRequest {
        /**
         * The command to execute.
         */
        private String command;
        
        /**
         * Whether to execute the command asynchronously.
         */
        private boolean async;
        
        /**
         * The working directory to execute the command in.
         */
        private String cwd;
        
        /**
         * Environment variables to set for the command.
         */
        private java.util.Map<String, String> env;
    }
}
