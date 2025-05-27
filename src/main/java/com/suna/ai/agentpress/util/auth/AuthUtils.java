package com.Nubian.ai.agentpress.util.auth;

import com.Nubian.ai.agentpress.service.DBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Utility class for authentication and authorization operations.
 * Provides methods for verifying access to threads, projects, and other resources.
 */
public final class AuthUtils {
    private static final Logger logger = LoggerFactory.getLogger(AuthUtils.class);

    /**
     * Verify that the user has access to the specified thread.
     *
     * @param dbConnection The database connection
     * @param userId The user ID
     * @param threadId The thread ID
     * @return A CompletableFuture that completes successfully if the user has access
     * @throws ResponseStatusException if the user does not have access to the thread
     */
    public static CompletableFuture<Void> verifyThreadAccess(DBConnection dbConnection, String userId, String threadId) {
        // Delegate to the asynchronous version
        return verifyThreadAccessAsync(dbConnection, userId, threadId);
    }

    /**
     * Verify that the user has access to the specified project.
     *
     * @param dbConnection The database connection
     * @param userId The user ID
     * @param projectId The project ID
     * @return A CompletableFuture that completes successfully if the user has access
     * @throws ResponseStatusException if the user does not have access to the project
     */
    public static CompletableFuture<Void> verifyProjectAccess(DBConnection dbConnection, String userId, String projectId) {
        // TODO: This method needs thorough testing to ensure the authorization logic and SQL queries are correct and cover all edge cases.
        String memberSql = "SELECT p.id FROM projects p " +
                           "JOIN project_members pm ON p.id = pm.project_id " +
                           "WHERE p.id = ? AND pm.user_id = ?";
        
        return dbConnection.queryForList(memberSql, projectId, userId)
            .thenCompose(memberResults -> {
                if (!memberResults.isEmpty()) {
                    logger.debug("User {} has member access to project {}", userId, projectId);
                    return CompletableFuture.completedFuture(null);
                }
                
                // If not found as a member, check if user is the creator
                String creatorSql = "SELECT id FROM projects WHERE id = ? AND created_by = ?";
                return dbConnection.queryForList(creatorSql, projectId, userId)
                    .thenAccept(creatorResults -> {
                        if (creatorResults.isEmpty()) {
                            logger.warn("User {} attempted to access unauthorized project {}", userId, projectId);
                            throw new ResponseStatusException(
                                HttpStatus.FORBIDDEN, 
                                "You do not have access to this project"
                            );
                        }
                        logger.debug("User {} has creator access to project {}", userId, projectId);
                    });
            })
            .exceptionally(e -> {
                logger.error("Error verifying project access: {}", e.getMessage(), e);
                throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Error verifying access: " + e.getMessage()
                );
            });
    }

    /**
     * Get the account ID for the specified thread.
     *
     * @param dbConnection The database connection
     * @param threadId The thread ID
     * @return A CompletableFuture that completes with the account ID
     * @throws ResponseStatusException if the thread is not found or other errors occur
     */
    public static CompletableFuture<String> getAccountIdFromThread(DBConnection dbConnection, String threadId) {
        // TODO: This method needs thorough testing to ensure the SQL query is correct and handles cases where the thread is not found.
        String sql = "SELECT a.id FROM accounts a " +
                     "JOIN projects p ON a.id = p.account_id " +
                     "JOIN threads t ON p.id = t.project_id " +
                     "WHERE t.id = ?";
        
        return dbConnection.queryForObject(sql, String.class, threadId)
            .thenApply(accountId -> {
                if (accountId == null) {
                    logger.warn("Thread {} not found or not associated with an account", threadId);
                    throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND, 
                        "Thread not found or not associated with an account"
                    );
                }
                logger.debug("Thread {} is associated with account {}", threadId, accountId);
                return accountId;
            })
            .exceptionally(e -> {
                logger.error("Error getting account ID from thread: {}", e.getMessage(), e);
                throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Error getting account information: " + e.getMessage()
                );
            });
    }

    /**
     * Get the project ID for the specified thread.
     *
     * @param dbConnection The database connection
     * @param threadId The thread ID
     * @return A CompletableFuture that completes with the project ID
     * @throws ResponseStatusException if the thread is not found or other errors occur
     */
    public static CompletableFuture<String> getProjectIdFromThread(DBConnection dbConnection, String threadId) {
        // TODO: This method needs thorough testing to ensure the SQL query is correct and handles cases where the thread is not found.
        String sql = "SELECT project_id FROM threads WHERE id = ?";
        
        return dbConnection.queryForObject(sql, String.class, threadId)
            .thenApply(projectId -> {
                if (projectId == null) {
                    logger.warn("Thread {} not found", threadId);
                    throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND, 
                        "Thread not found"
                    );
                }
                logger.debug("Thread {} is associated with project {}", threadId, projectId);
                return projectId;
            })
            .exceptionally(e -> {
                logger.error("Error getting project ID from thread: {}", e.getMessage(), e);
                throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Error getting project information: " + e.getMessage()
                );
            });
    }

    /**
     * Check if the user is an admin of the specified account.
     *
     * @param dbConnection The database connection
     * @param userId The user ID
     * @param accountId The account ID
     * @return A CompletableFuture that completes with true if the user is an admin, false otherwise
     * @throws ResponseStatusException if an error occurs
     */
    public static CompletableFuture<Boolean> isAccountAdmin(DBConnection dbConnection, String userId, String accountId) {
        // TODO: This method needs thorough testing to ensure the SQL query is correct.
        String sql = "SELECT 1 FROM account_members " +
                     "WHERE account_id = ? AND user_id = ? AND role = 'admin'";
        
        return dbConnection.queryForList(sql, accountId, userId) // MODIFIED to use queryForList
            .thenApply(list -> { // MODIFIED to handle List<Map<String, Object>>
                boolean isAdmin = !list.isEmpty(); // MODIFIED: if list is not empty, a row matched
                logger.debug("User {} is {}an admin of account {}", userId, isAdmin ? "" : "not ", accountId);
                return isAdmin;
            })
            .exceptionally(e -> {
                logger.error("Error checking if user is account admin: {}", e.getMessage(), e);
                throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Error checking admin status: " + e.getMessage()
                );
            });
    }

    /**
     * Verify that the user has admin access to the specified account.
     *
     * @param dbConnection The database connection
     * @param userId The user ID
     * @param accountId The account ID
     * @return A CompletableFuture that completes successfully if the user has admin access
     * @throws ResponseStatusException if the user is not an admin of the account
     */
    public static CompletableFuture<Void> verifyAccountAdminAccess(DBConnection dbConnection, String userId, String accountId) {
        // TODO: This method needs thorough testing to ensure the authorization logic is correct.
        return isAccountAdmin(dbConnection, userId, accountId)
            .thenAccept(isAdmin -> {
                if (!isAdmin) {
                    logger.warn("User {} attempted to access admin features for account {} without admin privileges", 
                               userId, accountId);
                    throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN, 
                        "You must be an admin to perform this operation"
                    );
                }
                logger.debug("User {} has admin access to account {}", userId, accountId);
            });
    }

    /**
     * Asynchronously verify that the user has access to the specified thread.
     *
     * @param dbConnection The database connection
     * @param userId The user ID
     * @param threadId The thread ID
     * @return A CompletableFuture that completes successfully if the user has access
     */
    public static CompletableFuture<Void> verifyThreadAccessAsync(DBConnection dbConnection, String userId, String threadId) {
        // TODO: This method needs thorough testing to ensure the authorization logic and SQL queries are correct and cover all edge cases.
        // Check for member access
        String memberSql = "SELECT t.id FROM threads t " +
                          "JOIN projects p ON t.project_id = p.id " +
                           "JOIN project_members pm ON p.id = pm.project_id " +
                           "WHERE t.id = ? AND pm.user_id = ?";
        
        return dbConnection.queryForList(memberSql, threadId, userId) // MODIFIED to use queryForList
            .thenCompose(memberList -> { // MODIFIED to handle List
                if (memberList != null && !memberList.isEmpty()) { // MODIFIED check
                    return CompletableFuture.completedFuture(null); // User is a member
                }
                
                // Check if user is the creator
                String creatorSql = "SELECT t.id FROM threads t " +
                                   "JOIN projects p ON t.project_id = p.id " +
                                   "WHERE t.id = ? AND p.created_by = ?";
                return dbConnection.queryForList(creatorSql, threadId, userId) // MODIFIED to use queryForList
                    .thenAccept(creatorList -> { // MODIFIED to handle List
                        if (creatorList == null || creatorList.isEmpty()) { // MODIFIED check
                            logger.warn("User {} attempted to access unauthorized thread {}", userId, threadId);
                            throw new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "You do not have access to this thread"
                            );
                        }
                    });
            })
            .exceptionally(e -> {
                logger.error("Error verifying thread access asynchronously: {}", e.getMessage(), e);
                throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Error verifying access: " + e.getMessage()
                );
            });
    }

    /**
     * Private constructor to prevent instantiation.
     */
    private AuthUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
