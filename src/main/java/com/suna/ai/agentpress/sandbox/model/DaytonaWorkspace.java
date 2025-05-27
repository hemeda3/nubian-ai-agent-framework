package com.Nubian.ai.agentpress.sandbox.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Model for Daytona workspace data returned from the API.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DaytonaWorkspace {
    /**
     * The workspace ID.
     */
    private String id;
    
    /**
     * The workspace name.
     */
    private String name;
    
    /**
     * The workspace state (e.g., "running", "stopped", "archived").
     */
    private String state;
    
    /**
     * The creation time of the workspace.
     */
    private LocalDateTime createdAt;
    
    /**
     * The ID of the organization that owns the workspace.
     */
    private String organizationId;
    
    /**
     * The ID of the user that created the workspace.
     */
    private String userId;
    
    /**
     * The image used for this workspace.
     */
    private String image;
    
    /**
     * Environment variables for the workspace.
     */
    private Map<String, String> env;
    
    /**
     * Labels for the workspace (for metadata and filtering).
     */
    private Map<String, String> labels;
    
    /**
     * Resources allocated to the workspace.
     */
    private WorkspaceResources resources;
    
    /**
     * Access control for the workspace.
     */
    private boolean isPublic;
    
    /**
     * Last used timestamp.
     */
    private LocalDateTime lastUsedAt;
    
    /**
     * URL for VNC access to the workspace.
     */
    private String vncUrl;
    
    /**
     * URL for website access to the workspace.
     */
    private String websiteUrl;
    
    /**
     * Get the VNC URL for this workspace.
     * 
     * @return The VNC URL, or null if not available
     */
    public String getVncUrl() {
        if (vncUrl != null) {
            return vncUrl;
        }
        
        // Construct VNC URL based on workspace ID if not explicitly set
        if (id != null) {
            return "https://vnc." + id + ".daytona.io";
        }
        
        return null;
    }
    
    /**
     * Get the website URL for this workspace.
     * 
     * @return The website URL, or null if not available
     */
    public String getWebsiteUrl() {
        if (websiteUrl != null) {
            return websiteUrl;
        }
        
        // Construct website URL based on workspace ID if not explicitly set
        if (id != null) {
            return "https://web." + id + ".daytona.io";
        }
        
        return null;
    }
    
    /**
     * Resources model for a Daytona workspace.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WorkspaceResources {
        /**
         * CPU cores allocated.
         */
        private int cpu;
        
        /**
         * Memory in GB allocated.
         */
        private int memory;
        
        /**
         * Disk space in GB allocated.
         */
        private int disk;
    }
}
