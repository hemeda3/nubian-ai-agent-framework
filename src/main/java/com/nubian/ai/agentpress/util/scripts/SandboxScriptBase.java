package com.nubian.ai.agentpress.util.scripts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Base class for sandbox maintenance scripts.
 * This class provides common functionality for scripts that operate on sandboxes.
 */
@Component
public abstract class SandboxScriptBase {
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    
    /**
     * Execute the script.
     *
     * @param args Command-line arguments
     * @return CompletableFuture that completes when the script finishes
     */
    public abstract CompletableFuture<Void> execute(String[] args);
    
    /**
     * Display a confirmation prompt and wait for user input.
     *
     * @param message The message to display
     * @param isDestructive Whether the operation is destructive
     * @return true if the user confirmed, false otherwise
     */
    protected boolean confirmOperation(String message, boolean isDestructive) {
        System.out.println();
        if (isDestructive) {
            System.out.println("⚠️  WARNING: " + message + " ⚠️");
            System.out.println("This action cannot be undone!");
        } else {
            System.out.println(message);
        }
        
        System.out.print("\nAre you sure you want to proceed? (TRUE/FALSE): ");
        String input = System.console().readLine().trim().toUpperCase();
        
        return "TRUE".equals(input);
    }
    
    /**
     * Print a progress update.
     *
     * @param current Current progress
     * @param total Total items to process
     * @param processedCount Number of successfully processed items
     * @param failedCount Number of failed items
     */
    protected void printProgress(int current, int total, int processedCount, int failedCount) {
        double percentage = (double) current / total * 100;
        System.out.printf("Progress: %d/%d items processed (%.1f%%)\n", current, total, percentage);
        System.out.printf("  - Processed: %d, Failed: %d\n", processedCount, failedCount);
    }
    
    /**
     * Print a summary of the operation.
     *
     * @param total Total items processed
     * @param processedCount Number of successfully processed items
     * @param failedCount Number of failed items
     * @param dryRun Whether this was a dry run
     */
    protected void printSummary(int total, int processedCount, int failedCount, boolean dryRun) {
        System.out.println("\nOperation Summary:");
        System.out.println("Total items: " + total);
        
        if (dryRun) {
            System.out.println("DRY RUN: No changes were made");
        } else {
            System.out.println("Successfully processed: " + processedCount);
            System.out.println("Failed to process: " + failedCount);
        }
    }
    
    /**
     * Validate required environment variables.
     *
     * @param requiredVars List of required environment variable names
     * @throws IllegalStateException if any required variables are missing
     */
    protected void validateEnvironment(List<String> requiredVars) {
        StringBuilder missingVars = new StringBuilder();
        
        for (String varName : requiredVars) {
            if (System.getenv(varName) == null || System.getenv(varName).isEmpty()) {
                if (missingVars.length() > 0) {
                    missingVars.append(", ");
                }
                missingVars.append(varName);
            }
        }
        
        if (missingVars.length() > 0) {
            throw new IllegalStateException("Missing required environment variables: " + missingVars);
        }
    }
    
    /**
     * Utility method to safely parse command-line arguments into options.
     *
     * @param args Command-line arguments
     * @param defaultOptions Default option values
     * @return Map of option names to values
     */
    protected Map<String, Object> parseArgs(String[] args, Map<String, Object> defaultOptions) {
        // This is a simplified implementation
        // In a real application, this would use a proper argument parsing library
        // or implement more robust parsing logic
        
        Map<String, Object> options = Map.copyOf(defaultOptions);
        
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            
            if (arg.startsWith("--")) {
                String optionName = arg.substring(2);
                
                // Check for boolean flags
                if (defaultOptions.containsKey(optionName) && defaultOptions.get(optionName) instanceof Boolean) {
                    options.put(optionName, true);
                    continue;
                }
                
                // Check for options with values
                if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                    String optionValue = args[i + 1];
                    
                    // Convert value to appropriate type based on default
                    Object defaultValue = defaultOptions.get(optionName);
                    if (defaultValue instanceof Integer) {
                        options.put(optionName, Integer.parseInt(optionValue));
                    } else if (defaultValue instanceof Double) {
                        options.put(optionName, Double.parseDouble(optionValue));
                    } else if (defaultValue instanceof Boolean) {
                        options.put(optionName, Boolean.parseBoolean(optionValue));
                    } else {
                        options.put(optionName, optionValue);
                    }
                    
                    i++; // Skip the value in the next iteration
                }
            }
        }
        
        return options;
    }
}
