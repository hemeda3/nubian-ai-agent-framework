package com.nubian.ai.agentpress.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

/**
 * Represents a user account in the system.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Account {
    private String id;
    private String name;
    private Timestamp createdAt;
    private Timestamp updatedAt;
}
