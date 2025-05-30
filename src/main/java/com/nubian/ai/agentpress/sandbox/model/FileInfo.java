package com.nubian.ai.agentpress.sandbox.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Model for file information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileInfo {
    /**
     * The name of the file.
     */
    private String name;
    
    /**
     * The full path of the file.
     */
    private String path;
    
    /**
     * Whether the file is a directory.
     */
    private boolean isDir;
    
    /**
     * The size of the file in bytes.
     */
    private long size;
    
    /**
     * The last modification time of the file.
     */
    private LocalDateTime modTime;
    
    /**
     * The permissions of the file (optional).
     */
    private String permissions;
}
