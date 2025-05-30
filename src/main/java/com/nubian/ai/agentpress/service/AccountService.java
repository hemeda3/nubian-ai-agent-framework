package com.nubian.ai.agentpress.service;

import com.nubian.ai.agentpress.model.Account;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service for managing user accounts.
 * Interacts with the database to retrieve and create accounts.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    public static final String DEMO_USER_MOCK_ACCOUNT_ID = "mock-account-id-for-demo-purposes"; // Added mock account ID
    private final DBConnection dbConnection;

    /**
     * Retrieves an account by primary owner user ID, or creates a new one if it doesn't exist.
     *
     * @param primaryOwnerUserId The ID of the primary owner user.
     * @return A CompletableFuture that resolves to the Account.
     */
    public CompletableFuture<Account> getOrCreateAccountForUser(String primaryOwnerUserId) {
        // Explicitly query the basejump.accounts table
        Map<String, Object> conditions = new HashMap<>();
        conditions.put("primary_owner_user_id", primaryOwnerUserId);
        return dbConnection.queryForList("basejump.accounts", conditions)
                .thenCompose(accounts -> {
                    if (accounts != null && !accounts.isEmpty()) {
                        log.info("Found existing account for primary owner user: {}", primaryOwnerUserId);
                        Map<String, Object> accountMap = accounts.get(0);
                        return CompletableFuture.completedFuture(mapToAccount(accountMap));
                    } else {
                        log.info("No account found for primary owner user: {}. Creating new account.", primaryOwnerUserId);
                        return createAccount(primaryOwnerUserId);
                    }
                });
    }

    /**
     * Creates a new account in the database.
     *
     * @param primaryOwnerUserId The primary owner user ID to associate with the new account
     * @return A CompletableFuture that resolves to the newly created Account.
     */
    private CompletableFuture<Account> createAccount(String primaryOwnerUserId) {
        String accountId = UUID.randomUUID().toString();
        String accountName = "User " + primaryOwnerUserId.substring(0, Math.min(8, primaryOwnerUserId.length())) + " Account";
        String nowIsoString = Instant.now().toString(); // Use ISO 8601 string format

        Map<String, Object> accountData = new HashMap<>();
        accountData.put("id", accountId);
        accountData.put("primary_owner_user_id", primaryOwnerUserId);
        accountData.put("name", accountName);
        accountData.put("personal_account", true); // Assuming it's a personal account for the demo
        accountData.put("created_at", nowIsoString);
        accountData.put("updated_at", nowIsoString);
        accountData.put("created_by", primaryOwnerUserId); // Set created_by to the primary owner user ID
        accountData.put("updated_by", primaryOwnerUserId); // Set updated_by to the primary owner user ID

        // Explicitly insert into the basejump.accounts table
        return dbConnection.insert("basejump.accounts", accountData, false)
                .thenApply(insertedData -> {
                    log.info("Created new account {} ({}) for primary owner user {}", accountName, accountId, primaryOwnerUserId);
                    return mapToAccount(insertedData);
                });
    }

    /**
     * Maps a database result map to an Account object.
     *
     * @param accountMap The map containing account data from the database.
     * @return An Account object.
     */
    private Account mapToAccount(Map<String, Object> accountMap) {
        if (accountMap == null) {
            log.error("Account map is null, cannot map to Account object.");
            return null;
        }
        String id = (String) accountMap.get("id");
        String name = (String) accountMap.get("name");
        
        // Handle different types for timestamps (String for ISO, Timestamp for direct DB objects)
        Timestamp createdAt = null;
        Object createdAtObj = accountMap.get("created_at");
        if (createdAtObj instanceof String) {
            try {
                createdAt = Timestamp.from(Instant.parse((String) createdAtObj));
            } catch (Exception e) {
                log.warn("Failed to parse created_at timestamp from String: {}", createdAtObj, e);
            }
        } else if (createdAtObj instanceof Timestamp) {
            createdAt = (Timestamp) createdAtObj;
        }

        Timestamp updatedAt = null;
        Object updatedAtObj = accountMap.get("updated_at");
        if (updatedAtObj instanceof String) {
            try {
                updatedAt = Timestamp.from(Instant.parse((String) updatedAtObj));
            } catch (Exception e) {
                log.warn("Failed to parse updated_at timestamp from String: {}", updatedAtObj, e);
            }
        } else if (updatedAtObj instanceof Timestamp) {
            updatedAt = (Timestamp) updatedAtObj;
        }

        if (id == null) {
            log.error("Account ID is null in mapToAccount for map: {}", accountMap);
            return null;
        }
        if (name == null) {
            log.warn("Account name is null in mapToAccount for map: {}", accountMap);
        }
        if (createdAt == null) {
            log.warn("Account createdAt is null or unparseable in mapToAccount for map: {}", accountMap);
        }
        if (updatedAt == null) {
            log.warn("Account updatedAt is null or unparseable in mapToAccount for map: {}", accountMap);
        }

        return new Account(id, name, createdAt, updatedAt);
    }
}
