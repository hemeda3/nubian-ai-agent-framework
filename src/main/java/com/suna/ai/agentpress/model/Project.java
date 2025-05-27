package com.Nubian.ai.agentpress.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

/**
 * Represents a project in the system.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Project {
    private String id;
    private String name;
    private String accountId;
    private String createdBy;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private String sandboxId; // Storing just the ID for simplicity, full sandbox details might be in a separate model/service
}
