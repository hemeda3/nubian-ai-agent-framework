package com.Nubian.ai.agentpress.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

/**
 * Represents a conversation thread in the system.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Thread {
    private String id;
    private String projectId;
    private String accountId;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
