package com.nubian.ai.agentpress;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import com.nubian.ai.agentpress.model.Account;
import com.nubian.ai.agentpress.model.LlmMessage;
import com.nubian.ai.agentpress.model.Message;
import com.nubian.ai.agentpress.model.ProcessorConfig;
import com.nubian.ai.agentpress.model.Project;
import com.nubian.ai.agentpress.service.AccountService;
import com.nubian.ai.agentpress.service.DBConnection;
import com.nubian.ai.agentpress.service.ProjectService;
import com.nubian.ai.agentpress.service.ThreadManager;
import com.nubian.ai.agentpress.tool.WebSearchTool;

/**
 * Demo class for the AgentPress framework.
 * 
 * This class demonstrates core capabilities of the AgentPress framework,
 * including thread management, tool registration, and running LLM conversations.
 */
@SpringBootApplication
public class AgentPressDemo {
    private static final Logger logger = LoggerFactory.getLogger(AgentPressDemo.class);
    
    /**
     * Register tools with the ThreadManager.
     * 
     * @param threadManager The thread manager
     * @param processorConfig The processor configuration
     * @return The command line runner
     */
    @Bean
    public CommandLineRunner agentPressRunner(
            ThreadManager threadManager,
            ProcessorConfig processorConfig,
            WebSearchTool webSearchTool, // Inject WebSearchTool here
            DBConnection dbConnection, // Inject DBConnection
            AccountService accountService, // Inject AccountService
            ProjectService projectService // Inject ProjectService
    ) {
        return args -> {
            // Register tools at the beginning of the demo
            logger.info("Registering tools for AgentPress demo...");
            threadManager.addTool(webSearchTool, null); // Register all functions
            logger.info("Tools registered.");

            logger.info("Starting AgentPress demo...");

            // Use a specific user ID from the database
            String demoUserId = "daa45dcd-a807-4fa8-b1c5-7b2a9ed61c7d";
            
            // Get or create an account for the demo user
            logger.info("Getting or creating account for user: {}", demoUserId);
            Account account = accountService.getOrCreateAccountForUser(demoUserId).join();
            String accountId = account.getId();
            logger.info("Using account: {} ({})", account.getName(), accountId);
            
            // Get or create a project for this account
            logger.info("Getting or creating project for account: {}", accountId);
            Project project = projectService.getOrCreateProjectForAccount(accountId, demoUserId).join();
            String projectId = project.getId();
            logger.info("Using project: {} ({})", project.getName(), projectId);
            
            // Now create the thread with the valid project and account IDs
            com.nubian.ai.agentpress.model.Thread thread = threadManager.createThread(projectId, accountId);
            String threadId = thread.getId();
            logger.info("Created new thread with ID: {}", threadId);
            
            // Add a system message to set the assistant's behavior
            LlmMessage systemPrompt = new LlmMessage("system", 
                    "You are a helpful assistant. Answer the user's questions concisely and accurately.");

            LlmMessage initialUserMessage = new LlmMessage("user", "Hello! Can you tell me about Java Spring Boot? And what's the weather in Paris?");
            
            // Add the initial user message to the database
            CompletableFuture<Message> userMessageFuture = threadManager.addMessage(
                    threadId,
                    "user",
                    Map.of("role", "user", "content", initialUserMessage.getContent()),
                    false,
                    null
            );
            // Wait for the message to be saved
            userMessageFuture.join();
            logger.info("Added user message to thread {}", threadId);
            
            // Run the thread
            CompletableFuture<List<Message>> responseFuture = threadManager.runThread(
                    threadId,
                    systemPrompt,
                    true, // stream
                    null, // No temporary message needed as it's persisted
                    "gpt-4o", // model
                    0.7f, // temperature
                    1000, // max tokens
                    processorConfig,
                    "auto", // tool choice
                    3, // max auto continues
                    5, // max XML tool calls
                    true, // include XML examples
                    true, // enable thinking
                    "medium", // reasoning effort
                    true, // enable context manager
                    "demo-user", // userId (placeholder)
                    UUID.randomUUID().toString(), // runId (placeholder)
                    java.time.Instant.now() // startTime (placeholder)
            );
            
            // Wait for the response
            List<Message> responseMessages = responseFuture.join();
            
            // Print the response
            logger.info("Received {} messages in response", responseMessages.size());
            for (Message message : responseMessages) {
                if ("assistant".equals(message.getType())) {
                    Object content = message.getContent();
                    if (content instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> contentMap = (Map<String, Object>) content;
                        if (contentMap.containsKey("content")) {
                            logger.info("Assistant: {}", contentMap.get("content"));
                        }
                        if (contentMap.containsKey("tool_calls")) {
                            logger.info("Assistant Tool Calls: {}", contentMap.get("tool_calls"));
                        }
                    }
                } else if ("tool".equals(message.getType())) {
                     logger.info("Tool Result: {}", message.getContent());
                }
            }

            logger.info("AgentPress demo completed");
        };
    }
}
