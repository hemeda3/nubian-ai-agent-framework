package com.nubian.ai.agentpress.sandbox.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.nubian.ai.agentpress.sandbox.SandboxFileService;
import com.nubian.ai.agentpress.sandbox.service.SandboxService;
import com.nubian.ai.agentpress.sandbox.model.FileInfo;
import com.nubian.ai.agentpress.service.DBConnection;
import com.nubian.ai.agentpress.util.auth.JwtUtils;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller for sandbox operations.
 */
@RestController
@RequestMapping("/api")
public class SandboxController {
    private static final Logger logger = LoggerFactory.getLogger(SandboxController.class);
    
    @Autowired
    private SandboxService sandboxService;
    
    @Autowired
    private SandboxFileService sandboxFileService;
    
    @Autowired
    private DBConnection dbConnection;
    
    @Autowired
    private JwtUtils jwtUtils;
    
    /**
     * Create a file in the sandbox using direct file upload.
     *
     * @param sandboxId The sandbox ID
     * @param path The path to upload the file to
     * @param file The file to upload
     * @param authHeader The Authorization header
     * @return ResponseEntity with status
     */
    @PostMapping("/sandboxes/{sandboxId}/files")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> createFile(
            @PathVariable String sandboxId,
            @RequestParam String path,
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        
        // Normalize the path to handle UTF-8 encoding correctly
        final String normalizedPath = sandboxFileService.normalizePath(path);
        
        String userId = jwtUtils.getOptionalUserIdFromHeader(authHeader).orElse(null);
        logger.info("Received file upload request for sandbox {}, path: {}, user_id: {}", sandboxId, normalizedPath, userId);
        
        return verifySandboxAccess(sandboxId, userId)
            .thenCompose(project -> {
                try {
                    // Get sandbox - we need to ensure it's running but we don't need the actual workspace object
                    return sandboxService.getOrStartSandbox(sandboxId)
                        .thenCompose(sandbox -> {
                            try {
                                // Read file content
                                byte[] content = file.getBytes();
                                
                                // Upload file - we use the sandboxId directly, not the workspace object
                                return sandboxFileService.uploadFile(sandboxId, normalizedPath, content)
                                    .thenApply(v -> {
                                        logger.info("File created at {} in sandbox {}", normalizedPath, sandboxId);
                                        
                                        Map<String, Object> response = Map.of(
                                            "status", "success",
                                            "created", true,
                                            "path", normalizedPath
                                        );
                                        
                                        return ResponseEntity.ok(response);
                                    });
                            } catch (IOException e) {
                                logger.error("Error reading file content: {}", e.getMessage(), e);
                                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error reading file content: " + e.getMessage());
                            }
                        });
                } catch (Exception e) {
                    logger.error("Error creating file in sandbox {}: {}", sandboxId, e.getMessage(), e);
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
                }
            });
    }
    
    /**
     * List files and directories at the specified path.
     *
     * @param sandboxId The sandbox ID
     * @param path The path to list files from
     * @param authHeader The Authorization header
     * @return ResponseEntity with file list
     */
    @GetMapping("/sandboxes/{sandboxId}/files")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> listFiles(
            @PathVariable String sandboxId,
            @RequestParam String path,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        
        // Normalize the path to handle UTF-8 encoding correctly
        final String normalizedPath = sandboxFileService.normalizePath(path);
        
        String userId = jwtUtils.getOptionalUserIdFromHeader(authHeader).orElse(null);
        logger.info("Received list files request for sandbox {}, path: {}, user_id: {}", sandboxId, normalizedPath, userId);
        
        return verifySandboxAccess(sandboxId, userId)
            .thenCompose(project -> {
                try {
                    // Get sandbox - we need to ensure it's running but don't need the workspace object
                    return sandboxService.getOrStartSandbox(sandboxId)
                        .thenCompose(sandbox -> {
                            // List files - use the sandboxId directly
                            return sandboxFileService.listFiles(sandboxId, normalizedPath)
                                .thenApply(files -> {
                                    logger.info("Successfully listed {} files in sandbox {}", files.size(), sandboxId);
                                    
                                    Map<String, Object> response = Map.of(
                                        "files", files
                                    );
                                    
                                    return ResponseEntity.ok(response);
                                });
                        });
                } catch (Exception e) {
                    logger.error("Error listing files in sandbox {}: {}", sandboxId, e.getMessage(), e);
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
                }
            });
    }
    
    /**
     * Read a file from the sandbox.
     *
     * @param sandboxId The sandbox ID
     * @param path The path to read the file from
     * @param authHeader The Authorization header
     * @return ResponseEntity with file content
     */
    @GetMapping("/sandboxes/{sandboxId}/files/content")
    public CompletableFuture<ResponseEntity<byte[]>> readFile(
            @PathVariable String sandboxId,
            @RequestParam String path,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        
        // Normalize the path to handle UTF-8 encoding correctly
        String originalPath = path;
        final String normalizedPath = sandboxFileService.normalizePath(path);
        
        String userId = jwtUtils.getOptionalUserIdFromHeader(authHeader).orElse(null);
        logger.info("Received file read request for sandbox {}, path: {}, user_id: {}", sandboxId, normalizedPath, userId);
        
        if (!originalPath.equals(normalizedPath)) {
            logger.info("Normalized path from '{}' to '{}'", originalPath, normalizedPath);
        }
        
        return verifySandboxAccess(sandboxId, userId)
            .thenCompose(project -> {
                try {
                    // Get sandbox - we need to ensure it's running but don't need the workspace object
                    return sandboxService.getOrStartSandbox(sandboxId)
                        .thenCompose(sandbox -> {
                            // Verify the file exists
                            String filename = normalizedPath.substring(normalizedPath.lastIndexOf("/") + 1);
                            String parentDir = normalizedPath.substring(0, normalizedPath.lastIndexOf("/"));
                            
                            return sandboxFileService.listFiles(sandboxId, parentDir)
                                .thenCompose(files -> {
                                    // Look for the target file with exact name match
                                    boolean fileExists = files.stream()
                                        .anyMatch(file -> file.getName().equals(filename));
                                    
                                    if (!fileExists) {
                                        logger.warn("File not found: {} in sandbox {}", normalizedPath, sandboxId);
                                        
                                        // Try to find similar files to help diagnose
                                        List<String> closeMatches = files.stream()
                                            .filter(file -> file.getName().toLowerCase().contains(filename.toLowerCase()))
                                            .map(FileInfo::getName)
                                            .toList();
                                        
                                        String errorDetail = "File '" + filename + "' not found in directory '" + parentDir + "'";
                                        
                                        if (!closeMatches.isEmpty()) {
                                            errorDetail += ". Similar files in the directory: " + String.join(", ", closeMatches);
                                        }
                                        
                                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, errorDetail);
                                    }
                                    
                                    // Read file - use sandboxId directly
                                    return sandboxFileService.downloadFile(sandboxId, normalizedPath)
                                        .thenApply(content -> {
                                            logger.info("Successfully read file {} from sandbox {}", filename, sandboxId);
                                            
                                            // Ensure proper encoding by explicitly using UTF-8 for the filename in Content-Disposition header
                                            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8)
                                                .replace("+", "%20");
                                            String contentDisposition = "attachment; filename*=UTF-8''" + encodedFilename;
                                            
                                            return ResponseEntity.ok()
                                                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                                                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                                                .body(content);
                                        });
                                });
                        });
                } catch (ResponseStatusException e) {
                    // Re-throw ResponseStatusException without wrapping
                    throw e;
                } catch (Exception e) {
                    logger.error("Error reading file in sandbox {}: {}", sandboxId, e.getMessage(), e);
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
                }
            });
    }
    
    /**
     * Ensure that a project's sandbox is active and running.
     * Checks the sandbox status and starts it if it's not running.
     *
     * @param projectId The project ID
     * @param authHeader The Authorization header
     * @return ResponseEntity with status
     */
    @PostMapping("/project/{projectId}/sandbox/ensure-active")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> ensureProjectSandboxActive(
            @PathVariable String projectId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        
        String userId = jwtUtils.getOptionalUserIdFromHeader(authHeader).orElse(null);
        logger.info("Received ensure sandbox active request for project {}, user_id: {}", projectId, userId);
        
        // Get sandbox ID from project data by querying the database
        return getProjectSandboxId(projectId, userId)
            .thenCompose(sandboxId -> {
                // Get or start the sandbox
                logger.info("Ensuring sandbox {} is active for project {}", sandboxId, projectId);
                return sandboxService.getOrStartSandbox(sandboxId)
                    .thenApply(sandbox -> {
                        logger.info("Successfully ensured sandbox {} is active for project {}", sandboxId, projectId);
                        
                        Map<String, Object> response = Map.of(
                            "status", "success",
                            "sandbox_id", sandboxId,
                            "message", "Sandbox is active"
                        );
                        
                        return ResponseEntity.ok(response);
                    });
            })
            .exceptionally(e -> {
                logger.error("Error ensuring sandbox is active for project {}: {}", projectId, e.getMessage(), e);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
            });
    }
    
    /**
     * Get the sandbox ID for a project by querying the database.
     *
     * @param projectId The project ID
     * @param userId The user ID for access verification
     * @return CompletableFuture containing the sandbox ID
     */
    private CompletableFuture<String> getProjectSandboxId(String projectId, String userId) {
        // Query the database to get the sandbox information for this project
        String sql = "SELECT sandbox_id FROM projects WHERE id = ?";
        
        return dbConnection.queryForList(sql, projectId)
            .thenCompose(results -> {
                if (results.isEmpty()) {
                    CompletableFuture<String> future = new CompletableFuture<>();
                    future.completeExceptionally(new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found: " + projectId));
                    return future;
                }
                
                Object sandboxIdObj = results.get(0).get("sandbox_id");
                
                if (sandboxIdObj == null) {
                    // If no sandbox ID exists, create one based on the project ID
                    String sandboxId = "sandbox_" + projectId;
                    
                    // Update the project with the new sandbox ID
                    String updateSql = "UPDATE projects SET sandbox_id = ? WHERE id = ?";
                    return dbConnection.updateAsync(updateSql, sandboxId, projectId)
                        .thenApply(rowsUpdated -> {
                            logger.info("Created new sandbox ID {} for project {}", sandboxId, projectId);
                            return sandboxId;
                        });
                }
                
                String sandboxId = (String) sandboxIdObj;
                logger.debug("Found existing sandbox ID {} for project {}", sandboxId, projectId);
                return CompletableFuture.completedFuture(sandboxId);
            })
            .exceptionally(e -> {
                if (e.getCause() instanceof ResponseStatusException) {
                    throw (ResponseStatusException) e.getCause();
                }
                logger.error("Error getting sandbox ID for project {}: {}", projectId, e.getMessage(), e);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Error retrieving sandbox information: " + e.getMessage());
            });
    }
    
    /**
     * Verify that a user has access to a specific sandbox based on account membership.
     *
     * @param sandboxId The sandbox ID to check access for
     * @param userId The user ID to check permissions for
     * @return CompletableFuture containing project data if access is allowed
     * @throws ResponseStatusException if the user doesn't have access to the sandbox
     */
    private CompletableFuture<Map<String, Object>> verifySandboxAccess(String sandboxId, String userId) {
        // Query the database to find the project that owns this sandbox
        String sql = "SELECT p.id, p.name, p.account_id, p.created_by " +
                    "FROM projects p WHERE p.sandbox_id = ?";
        
        return dbConnection.queryForList(sql, sandboxId)
            .thenCompose(results -> {
                if (results.isEmpty()) {
                    CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
                    future.completeExceptionally(new ResponseStatusException(HttpStatus.NOT_FOUND, "Sandbox not found"));
                    return future;
                }
                
                Map<String, Object> project = results.get(0);
                String projectId = (String) project.get("id");
                String accountId = (String) project.get("account_id");
                String createdBy = (String) project.get("created_by");
                
                // Check if user has access to this project
                if (userId != null) {
                    // Check if user is the creator
                    if (userId.equals(createdBy)) {
                        logger.debug("User {} has access to sandbox {} as project creator", userId, sandboxId);
                        return CompletableFuture.completedFuture(project);
                    }
                    
                    // Check if user is a member of the account
                    String memberSql = "SELECT 1 FROM account_members WHERE account_id = ? AND user_id = ?";
                    return dbConnection.queryForList(memberSql, accountId, userId)
                        .thenCompose(memberResults -> {
                            if (!memberResults.isEmpty()) {
                                logger.debug("User {} has access to sandbox {} as account member", userId, sandboxId);
                                return CompletableFuture.completedFuture(project);
                            }
                            
                            // Check if user is a project member
                            String projectMemberSql = "SELECT 1 FROM project_members WHERE project_id = ? AND user_id = ?";
                            return dbConnection.queryForList(projectMemberSql, projectId, userId)
                                .thenCompose(projectMemberResults -> {
                                    if (!projectMemberResults.isEmpty()) {
                                        logger.debug("User {} has access to sandbox {} as project member", userId, sandboxId);
                                        return CompletableFuture.completedFuture(project);
                                    }
                                    
                                    // Check if project is public (allow access without authentication)
                                    String publicSql = "SELECT is_public FROM projects WHERE id = ?";
                                    return dbConnection.queryForList(publicSql, projectId)
                                        .thenCompose(publicResults -> {
                                            if (!publicResults.isEmpty()) {
                                                Boolean isPublic = (Boolean) publicResults.get(0).get("is_public");
                                                if (Boolean.TRUE.equals(isPublic)) {
                                                    logger.debug("User {} has access to sandbox {} - project is public", userId, sandboxId);
                                                    return CompletableFuture.completedFuture(project);
                                                }
                                            }
                                            
                                            // If we get here, the user doesn't have access
                                            logger.warn("User {} does not have access to sandbox {}", userId, sandboxId);
                                            CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
                                            future.completeExceptionally(new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to sandbox"));
                                            return future;
                                        });
                                });
                        });
                } else {
                    // No user ID provided, check if the project is public
                    String publicSql = "SELECT is_public FROM projects WHERE id = ?";
                    return dbConnection.queryForList(publicSql, projectId)
                        .thenCompose(publicResults -> {
                            if (!publicResults.isEmpty()) {
                                Boolean isPublic = (Boolean) publicResults.get(0).get("is_public");
                                if (Boolean.TRUE.equals(isPublic)) {
                                    logger.debug("Anonymous access allowed to sandbox {} - project is public", sandboxId);
                                    return CompletableFuture.completedFuture(project);
                                }
                            }
                            
                            // If we get here, anonymous access is not allowed
                            logger.warn("Anonymous access denied to sandbox {}", sandboxId);
                            CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
                            future.completeExceptionally(new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to sandbox"));
                            return future;
                        });
                }
            })
            .exceptionally(e -> {
                if (e.getCause() instanceof ResponseStatusException) {
                    throw (ResponseStatusException) e.getCause();
                }
                logger.error("Error verifying sandbox access: {}", e.getMessage(), e);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Error verifying access: " + e.getMessage());
            });
    }
}
