package com.Nubian.ai.agentpress.service.billing.impl;

import com.Nubian.ai.agentpress.service.billing.CustomerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.Nubian.ai.agentpress.service.DBConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of CustomerService that interacts with the database to manage customer records.
 */
public class CustomerServiceImpl implements CustomerService {

    private static final Logger logger = LoggerFactory.getLogger(CustomerServiceImpl.class);
    private final DBConnection dbConnection;
    private final ObjectMapper objectMapper;

    public CustomerServiceImpl(DBConnection dbConnection, ObjectMapper objectMapper) {
        this.dbConnection = dbConnection;
        this.objectMapper = objectMapper;
    }

    @Override
    public CompletableFuture<String> getCustomerId(String userId) {
        logger.debug("Getting customer ID for user: {}", userId);
        
        Map<String, Object> conditions = new HashMap<>();
        conditions.put("user_id", userId);
        
        return dbConnection.queryForList("billing_customers", conditions)
            .thenCompose(results -> {
                if (results != null && !results.isEmpty()) {
                    Map<String, Object> customerRecord = results.get(0);
                    String customerId = (String) customerRecord.get("customer_id");
                    if (customerId != null) {
                        return CompletableFuture.completedFuture(customerId);
                    }
                }
                // If customerId is null or results are empty, create a new customer record
                logger.warn("Customer ID not found for user {}, creating new record", userId);
                String newCustomerId = "cus_" + System.currentTimeMillis();
                
                Map<String, Object> customerData = new HashMap<>();
                customerData.put("user_id", userId);
                customerData.put("customer_id", newCustomerId);
                
                return dbConnection.insert("billing_customers", customerData, false)
                    .thenApply(insertedData -> {
                        if (insertedData != null && !insertedData.isEmpty()) {
                            logger.info("Successfully created new customer record for user {} with customer_id {}", userId, newCustomerId);
                        } else {
                            logger.error("Failed to create new customer record for user {}", userId);
                            // Optionally throw an exception or handle error case
                        }
                        return newCustomerId;
                    });
            });
    }

    @Override
    public CompletableFuture<Map<String, Object>> checkStatus(String customerId) {
        logger.debug("Checking status for customer: {}", customerId);
        
        Map<String, Object> conditions = new HashMap<>();
        conditions.put("customer_id", customerId);
        
        return dbConnection.queryForList("billing_subscriptions", conditions)
            .thenApply(results -> {
                if (results != null && !results.isEmpty()) {
                    // Assuming billing_subscriptions table directly contains the needed fields
                    // or that queryForList returns them as expected.
                    return results.get(0); 
                } else {
                    logger.warn("No subscription found for customer {}, returning default status", customerId);
                    Map<String, Object> defaultStatus = new HashMap<>();
                    defaultStatus.put("status", "free");
                    defaultStatus.put("tier", "free");
                    defaultStatus.put("last_payment", null);
                    return defaultStatus;
                }
            });
    }
}
