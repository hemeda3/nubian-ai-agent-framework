package com.nubian.ai.agentpress.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import reactor.core.publisher.Flux;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nubian.ai.agentpress.model.LlmMessage;
import com.nubian.ai.agentpress.model.Message;
import com.nubian.ai.agentpress.model.ProcessorConfig;
import com.nubian.ai.agentpress.model.Tool;
import com.nubian.ai.agentpress.model.ToolCall;
import com.nubian.ai.agentpress.model.Thread; // Import Thread model

import java.sql.Timestamp;
import java.time.Instant;

/**
 * Manages conversation threads with LLM models and tool execution.
 * 
 * Provides comprehensive conversation management, handling message threading,
 * tool registration, and LLM interactions with support for both standard and
 * XML-based tool execution patterns.
 */
@Service
public class ThreadManager {
    private static final Logger logger = LoggerFactory.getLogger(ThreadManager.class);
    
    private final DBConnection dbConnection;
    private final ToolRegistry toolRegistry;
    private final ResponseProcessor responseProcessor;
    private final ContextManager contextManager;
    private final LlmServiceFactory llmServiceFactory;
    private final ObjectMapper objectMapper;
    
    /**
     * Initialize ThreadManager.
     * 
     * @param dbConnection The database connection
     * @param toolRegistry The tool registry
     * @param responseProcessor The response processor
     * @param contextManager The context manager
     * @param llmServiceFactory The LLM service factory
     * @param objectMapper The object mapper
     */
    @Autowired
    public ThreadManager(
            DBConnection dbConnection,
            ToolRegistry toolRegistry,
            ResponseProcessor responseProcessor,
            ContextManager contextManager,
            LlmServiceFactory llmServiceFactory,
            ObjectMapper objectMapper) {
        this.dbConnection = dbConnection;
        this.toolRegistry = toolRegistry;
        this.responseProcessor = responseProcessor;
        this.contextManager = contextManager;
        this.llmServiceFactory = llmServiceFactory;
        this.objectMapper = objectMapper;
        
        logger.debug("Initialized ThreadManager");
    }
    
    /**
     * Creates a new conversation thread in the database using direct table access.
     *
     * @param projectId The ID of the project this thread belongs to.
     * @param accountId The ID of the account this thread belongs to.
     * @return The newly created Thread object.
     */
    public Thread createThread(String projectId, String accountId) {
        // If accountId is the mock ID, return a mock thread immediately
        if (AccountService.DEMO_USER_MOCK_ACCOUNT_ID.equals(accountId)) {
            String mockThreadId = UUID.randomUUID().toString();
            Timestamp now = Timestamp.from(Instant.now());
            logger.warn("Account ID matches DEMO_USER_MOCK_ACCOUNT_ID ({}). Returning mock thread {} to avoid foreign key violation.", accountId, mockThreadId);
            // Assuming projectId can also be a mock or a valid one if not tied to the mock account directly
            return new Thread(mockThreadId, projectId, accountId, now, now);
        }

        String threadId = UUID.randomUUID().toString();
        Timestamp now = Timestamp.from(Instant.now());
        
        try {
            // Validate UUIDs before insertion
            try {
                // Attempt to parse projectId and accountId as UUIDs to ensure they're valid
                if (projectId != null && !projectId.isEmpty()) {
                    UUID.fromString(projectId);
                }
                if (accountId != null && !accountId.isEmpty()) {
                    UUID.fromString(accountId);
                }
            } catch (IllegalArgumentException e) {
                logger.error("Invalid UUID format: projectId={}, accountId={}", projectId, accountId);
                throw new RuntimeException("Invalid UUID format for projectId or accountId. " +
                        "UUIDs must be in format: 00000000-0000-0000-0000-000000000000", e);
            }
            
            // Check if account exists before insertion, unless it's the mock account ID
            if (accountId != null && !accountId.isEmpty() && !AccountService.DEMO_USER_MOCK_ACCOUNT_ID.equals(accountId)) {
                try {
                    // Create a query to check if account exists
                    Map<String, Object> accountConditions = new HashMap<>();
                    accountConditions.put("id", accountId);
                    
                    // Use the Python-style schema method to explicitly select the basejump schema
                    CompletableFuture<List<Map<String, Object>>> accountFuture = 
                            dbConnection.queryForList("basejump.accounts", accountConditions); // MODIFIED
                    
                    logger.info("Checking if real account exists with ID: {} in basejump.accounts table", accountId);
                    
                    List<Map<String, Object>> accounts = accountFuture.join();
                    if (accounts.isEmpty()) {
                        logger.error("Real Account ID not found: {}", accountId);
                        throw new RuntimeException("Real Account ID not found: " + accountId + 
                                ". The account must exist before creating a thread.");
                    }
                } catch (Exception e) {
                    logger.error("Error checking if real account exists: {}", e.getMessage(), e);
                    throw new RuntimeException("Error checking if real account exists: " + e.getMessage(), e);
                }
            } else if (AccountService.DEMO_USER_MOCK_ACCOUNT_ID.equals(accountId)) {
                logger.warn("Skipping account existence check for DEMO_USER_MOCK_ACCOUNT_ID: {}", accountId);
            }
            
            // Check if project exists before insertion (if projectId is provided)
            if (projectId != null && !projectId.isEmpty()) {
                try {
                    // Create a query to check if project exists
                    Map<String, Object> projectConditions = new HashMap<>();
                    projectConditions.put("project_id", projectId);
                    
                    CompletableFuture<List<Map<String, Object>>> projectFuture = 
                            dbConnection.queryForList("projects", projectConditions); // MODIFIED
                    
                    List<Map<String, Object>> projects = projectFuture.join();
                    if (projects.isEmpty()) {
                        logger.error("Project ID not found: {}", projectId);
                        throw new RuntimeException("Project ID not found: " + projectId + 
                                ". The project must exist before creating a thread.");
                    }
                } catch (Exception e) {
                    logger.error("Error checking if project exists: {}", e.getMessage(), e);
                    throw new RuntimeException("Error checking if project exists: " + e.getMessage(), e);
                }
            }
            
            // Create a map with the thread data
            Map<String, Object> threadData = new HashMap<>();
            threadData.put("thread_id", threadId);
            threadData.put("project_id", projectId);
            threadData.put("account_id", accountId);
            
            // Format timestamps as ISO-8601 strings for proper PostgreSQL compatibility
            String createdAtStr = now.toInstant().toString();
            String updatedAtStr = now.toInstant().toString();
            threadData.put("created_at", createdAtStr);
            threadData.put("updated_at", updatedAtStr);
            
            // Use the table() method for Python API compatibility
            Map<String, Object> result = dbConnection.insert("threads", threadData, false).join(); // MODIFIED (upsert=false implies returning "representation")
            
            if (result == null || result.isEmpty()) {
                throw new RuntimeException("Failed to create thread - no response from database");
            }
            
            logger.info("Created new thread {} for project {} and account {}", threadId, projectId, accountId);
            return new Thread(threadId, projectId, accountId, now, now);
        } catch (Exception e) {
            logger.error("Error creating thread: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create thread", e);
        }
    }

    /**
     * Add a tool to the ThreadManager.
     * 
     * @param toolInstance The tool instance to register
     * @param functionNames Optional list of specific functions to register
     */
    public void addTool(Tool toolInstance, List<String> functionNames) {
        toolRegistry.registerTool(toolInstance, functionNames);
    }
    
    /**
     * Add a message to the thread in the database.
     * 
     * @param threadId The ID of the thread to add the message to
     * @param type The type of the message (e.g., 'text', 'image_url', 'tool_call', 'tool', 'user', 'assistant')
     * @param content The content of the message
     * @param isLlmMessage Flag indicating if the message originated from the LLM
     * @param metadata Optional dictionary for additional message metadata
     * @return A CompletableFuture that completes with the saved message
     */
    public CompletableFuture<Message> addMessage(
            String threadId,
            String type,
            Object content,
            boolean isLlmMessage,
            Map<String, Object> metadata) {
        
        logger.debug("Adding message of type '{}' to thread {}", type, threadId);
        
        Message message = new Message(threadId, type, content, isLlmMessage, metadata != null ? metadata : Map.of());
        return dbConnection.insertMessage(message);
    }
    
    /**
     * Get all messages for a thread.
     * 
     * @param threadId The ID of the thread to get messages for
     * @return A CompletableFuture that completes with the list of messages
     */
    public CompletableFuture<List<Map<String, Object>>> getMessages(String threadId) {
        return dbConnection.getMessages(threadId);
    }
    
    /**
     * Get LLM-formatted messages for a thread.
     * 
     * @param threadId The ID of the thread to get messages for
     * @return A CompletableFuture that completes with the list of LLM-formatted messages
     */
    public CompletableFuture<List<LlmMessage>> getLlmMessages(String threadId) {
        logger.debug("Getting LLM messages for thread {}", threadId);
        
        return dbConnection.getLlmFormattedMessages(threadId)
                .thenApply(messages -> {
                    try {
                        return messages.stream()
                                .map(this::convertToLlmMessage)
                                .filter(msg -> msg != null)
                                .toList();
                    } catch (Exception e) {
                        logger.error("Failed to convert messages to LLM format: {}", e.getMessage(), e);
                        return List.of();
                    }
                });
    }
    
    /**
     * Convert a database message to an LLM message.
     * 
     * @param message The database message
     * @return The LLM message, or null if conversion fails
     */
    @SuppressWarnings("unchecked")
    private LlmMessage convertToLlmMessage(Map<String, Object> message) {
        try {
            Object content = message.get("content");
            if (content instanceof String) {
                content = objectMapper.readValue((String) content, Map.class);
            }
            
            if (content instanceof Map) {
                Map<String, Object> contentMap = (Map<String, Object>) content;
                String role = (String) contentMap.get("role");
                String text = (String) contentMap.get("content");
                
                if (contentMap.containsKey("tool_calls")) {
                    // Handle tool calls
                    List<Map<String, Object>> toolCallsData = (List<Map<String, Object>>) contentMap.get("tool_calls");
                    if (toolCallsData != null && !toolCallsData.isEmpty()) {
                        // Convert to ToolCall objects
                        List<ToolCall> toolCalls = toolCallsData.stream()
                                .map(this::convertToToolCall)
                                .filter(tc -> tc != null)
                                .toList();
                        
                        if (!toolCalls.isEmpty()) {
                            return new LlmMessage(role, (Object)text, toolCalls);
                        }
                    }
                }
                
                if ("tool".equals(role) && contentMap.containsKey("tool_call_id")) {
                    // Handle tool result
                    String toolCallId = (String) contentMap.get("tool_call_id");
                    String name = (String) contentMap.get("name");
                    return new LlmMessage(toolCallId, name, (Object)text);
                }
                
                return new LlmMessage(role, (Object)text);
            }
            
            logger.error("Unexpected message content format: {}", content);
            return null;
        } catch (Exception e) {
            logger.error("Error converting message to LLM format: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Run a conversation thread with LLM integration and tool execution.
     * 
     * @param threadId The ID of the thread to run
     * @param systemPrompt System message to set the assistant's behavior
     * @param stream Use streaming API for the LLM response
     * @param temporaryMessage Optional temporary user message for this run only
     * @param llmModel The name of the LLM model to use
     * @param llmTemperature Temperature parameter for response randomness (0-1)
     * @param llmMaxTokens Maximum tokens in the LLM response
     * @param processorConfig Configuration for the response processor
     * @param toolChoice Tool choice preference ("auto", "required", "none")
     * @param nativeMaxAutoContinues Maximum number of automatic continuations
     * @param maxXmlToolCalls Maximum number of XML tool calls to allow (0 = no limit)
     * @param includeXmlExamples Whether to include XML tool examples in the system prompt
     * @param enableThinking Whether to enable thinking before making a decision
     * @param reasoningEffort The effort level for reasoning
     * @param enableContextManager Whether to enable automatic context summarization
     * @param userId The ID of the user initiating the run (for billing)
     * @param runId The ID of the agent run (for billing)
     * @param startTime The start time of the LLM call (for billing)
     * @return A CompletableFuture that completes with the list of response messages
     */
    public CompletableFuture<List<Message>> runThread(
            String threadId,
            LlmMessage systemPrompt,
            boolean stream,
            LlmMessage temporaryMessage,
            String llmModel,
            float llmTemperature,
            Integer llmMaxTokens,
            ProcessorConfig processorConfig,
            String toolChoice,
            int nativeMaxAutoContinues,
            int maxXmlToolCalls,
            boolean includeXmlExamples,
            boolean enableThinking,
            String reasoningEffort,
            boolean enableContextManager,
            String userId,
            String runId,
            Instant startTime) {
        
        logger.info("Starting thread execution for thread {}", threadId);
        logger.info("Using model: {}", llmModel);
        logger.info("Parameters: model={}, temperature={}, max_tokens={}", 
                llmModel, llmTemperature, llmMaxTokens);
        logger.info("Auto-continue: max={}, XML tool limit={}", 
                nativeMaxAutoContinues, maxXmlToolCalls);
        
        // Apply max_xml_tool_calls if specified and not already set in config
        if (maxXmlToolCalls > 0 && processorConfig.getMaxXmlToolCalls() == 0) {
            processorConfig.setMaxXmlToolCalls(maxXmlToolCalls);
        }
        
        // Create a working copy of the system prompt to potentially modify
        final LlmMessage workingSystemPrompt = new LlmMessage(systemPrompt.getRole(), systemPrompt.getContent());
        
        // Add XML examples to system prompt if requested
        if (includeXmlExamples && processorConfig.isXmlToolCalling()) {
            Map<String, String> xmlExamples = toolRegistry.getXmlExamples();
            if (!xmlExamples.isEmpty()) {
                StringBuilder examplesContent = new StringBuilder();
                examplesContent.append("\n--- XML TOOL CALLING ---\n\n");
                examplesContent.append("In this environment you have access to a set of tools you can use to answer the user's question. ");
                examplesContent.append("The tools are specified in XML format.\n");
                examplesContent.append("Format your tool calls using the specified XML tags. Place parameters marked as 'attribute' within the opening tag ");
                examplesContent.append("(e.g., `<tag attribute='value'>`). Place parameters marked as 'content' between the opening and closing tags. ");
                examplesContent.append("Place parameters marked as 'element' within their own child tags (e.g., `<tag><element>value</element></tag>`). ");
                examplesContent.append("Refer to the examples provided below for the exact structure of each tool.\n");
                examplesContent.append("String and scalar parameters should be specified as attributes, while content goes between tags.\n");
                examplesContent.append("Note that spaces for string values are not stripped. The output is parsed with regular expressions.\n\n");
                examplesContent.append("Here are the XML tools available with examples:\n");
                
                for (Map.Entry<String, String> entry : xmlExamples.entrySet()) {
                    examplesContent.append("<").append(entry.getKey()).append("> Example: ")
                            .append(entry.getValue()).append("\n");
                }
                
                // Append to the system prompt content
                workingSystemPrompt.setContent(workingSystemPrompt.getContent() + examplesContent.toString());
                
                logger.debug("Appended XML examples to system prompt content");
            }
        }
        
        // Create a BiFunction for adding messages that can be passed to the response processor
        final BiFunction<String, Message, CompletableFuture<Message>> addMessageCallback = 
                (tid, message) -> addMessage(
                        tid, 
                        message.getType(), 
                        message.getContent(), 
                        message.isLlmMessage(), 
                        message.getMetadata());
        
        // Create a simple implementation that doesn't use complex lambdas
        CompletableFuture<List<Message>> result = new CompletableFuture<>();
        
        // Start the process
        processThread(
            result,
            threadId,
            workingSystemPrompt,
            stream,
            temporaryMessage,
            llmModel,
            llmTemperature,
            llmMaxTokens,
            processorConfig,
            toolChoice,
            nativeMaxAutoContinues,
            maxXmlToolCalls,
            enableThinking,
            reasoningEffort,
            enableContextManager,
            addMessageCallback,
            0,
            new java.util.ArrayList<>(),
            userId,
            runId,
            startTime
        );
        
        return result;
    }
    
    /**
     * Process a thread recursively.
     * 
     * @param result The CompletableFuture to complete with the result
     * @param threadId The ID of the thread to run
     * @param workingSystemPrompt The system prompt
     * @param stream Whether to use streaming
     * @param temporaryMessage The temporary message
     * @param llmModel The LLM model to use
     * @param llmTemperature The temperature parameter
     * @param llmMaxTokens The maximum tokens parameter
     * @param processorConfig The processor configuration
     * @param toolChoice The tool choice preference
     * @param nativeMaxAutoContinues The maximum number of auto-continues
     * @param maxXmlToolCalls The maximum number of XML tool calls
     * @param enableThinking Whether to enable thinking
     * @param reasoningEffort The reasoning effort level
     * @param enableContextManager Whether to enable context management
     * @param addMessageCallback The callback for adding messages
     * @param autoContinueCount The current auto-continue count
     * @param allMessages The accumulated messages
     */
    private void processThread(
            CompletableFuture<List<Message>> result,
            String threadId,
            LlmMessage workingSystemPrompt,
            boolean stream,
            LlmMessage temporaryMessage,
            String llmModel,
            float llmTemperature,
            Integer llmMaxTokens,
            ProcessorConfig processorConfig,
            String toolChoice,
            int nativeMaxAutoContinues,
            int maxXmlToolCalls,
            boolean enableThinking,
            String reasoningEffort,
            boolean enableContextManager,
            BiFunction<String, Message, CompletableFuture<Message>> addMessageCallback,
            int autoContinueCount,
            List<Message> allMessages,
            String userId,
            String runId,
            Instant startTime) {
        
        // Get messages from thread for LLM call
        getLlmMessages(threadId)
            .thenCompose(messages -> {
                // Check token count before proceeding
                if (enableContextManager) {
                    return contextManager.getThreadTokenCount(threadId)
                        .thenCompose(tokenCount -> {
                            int tokenThreshold = contextManager.getTokenThreshold();
                            logger.info("Thread {} token count: {}/{} ({}%)",
                                    threadId, tokenCount, tokenThreshold,
                                    String.format("%.1f", (tokenCount / (float) tokenThreshold) * 100));
                            
                            if (tokenCount >= tokenThreshold) {
                                logger.info("Thread token count ({}) exceeds threshold ({}), summarizing...",
                                        tokenCount, tokenThreshold);
                                
                                // Create a callback for adding summary messages
                                final int finalTokenCount = tokenCount;
                                BiFunction<String, LlmMessage, CompletableFuture<Message>> summaryCallback =
                                        (tid, llmMsg) -> {
                                            Map<String, Object> metadata = Map.of("token_count", finalTokenCount);
                                            return addMessage(tid, "summary", llmMsg.getContent(), true, metadata);
                                        };
                                
                                return contextManager.checkAndSummarizeIfNeeded(
                                        threadId, 
                                        summaryCallback, 
                                        llmModel, 
                                        true,
                                        userId,
                                        runId,
                                        startTime)
                                        .thenCompose(summarized -> {
                                            if (summarized) {
                                                logger.info("Summarization complete, fetching updated messages with summary");
                                                return getLlmMessages(threadId);
                                            } else {
                                                logger.warn("Summarization failed or wasn't needed - proceeding with original messages");
                                                return CompletableFuture.completedFuture(messages);
                                            }
                                        });
                            } else {
                                return CompletableFuture.completedFuture(messages);
                            }
                        });
                } else {
                    logger.info("Automatic summarization disabled. Skipping token count check and summarization.");
                    return CompletableFuture.completedFuture(messages);
                }
            })
            .thenCompose(messages -> {
                // Prepare messages for LLM call + add temporary message if it exists
                List<LlmMessage> preparedMessages = new java.util.ArrayList<>();
                preparedMessages.add(workingSystemPrompt);
                
                // Find the last user message index
                int lastUserIndex = -1;
                for (int i = 0; i < messages.size(); i++) {
                    if ("user".equals(messages.get(i).getRole())) {
                        lastUserIndex = i;
                    }
                }
                
                // Insert temporary message before the last user message if it exists
                LlmMessage tempMsg = autoContinueCount == 0 ? temporaryMessage : null;
                if (tempMsg != null && lastUserIndex >= 0) {
                    preparedMessages.addAll(messages.subList(0, lastUserIndex));
                    preparedMessages.add(tempMsg);
                    preparedMessages.addAll(messages.subList(lastUserIndex, messages.size()));
                    logger.debug("Added temporary message before the last user message");
                } else {
                    // If no user message or no temporary message, just add all messages
                    preparedMessages.addAll(messages);
                    if (tempMsg != null) {
                        preparedMessages.add(tempMsg);
                        logger.debug("Added temporary message to the end of prepared messages");
                    }
                }
                
                // Prepare tools for LLM call
                List<Map<String, Object>> openApiToolSchemas = null;
                if (processorConfig.isNativeToolCalling()) {
                    openApiToolSchemas = toolRegistry.getOpenApiSchemas();
                    logger.debug("Retrieved {} OpenAPI tool schemas",
                            openApiToolSchemas != null ? openApiToolSchemas.size() : 0);
                }
                
                // Get the appropriate LLM service based on the model name
                LlmService llmService;
                if (llmModel != null && llmModel.startsWith("gpt-")) {
                    llmService = llmServiceFactory.getLlmService("openai");
                } else {
                    llmService = llmServiceFactory.getLlmService("google");
                }
                
                // Make LLM API call
                logger.debug("Making LLM API call using provider: {}", llmModel != null && llmModel.startsWith("gpt-") ? "openai" : "google");
                return llmService.makeLlmApiCall(
                        preparedMessages,
                        llmModel,
                        llmTemperature,
                        llmMaxTokens,
                        openApiToolSchemas,
                        toolChoice,
                        stream,
                        enableThinking,
                        reasoningEffort,
                        userId,
                        runId,
                        startTime
                )
                .thenCompose(llmResponse -> {
                    // Process LLM response using the ResponseProcessor
                    if (stream) {
                        logger.debug("Processing streaming response");
                        Flux<Message> messageFlux = responseProcessor.processStreamingResponse(
                                llmResponse,
                                threadId,
                                preparedMessages,
                                llmModel,
                                processorConfig
                        );
                        
                        // Manual conversion from Flux to CompletableFuture<List<Message>>
                        CompletableFuture<List<Message>> future = new CompletableFuture<>();
                        List<Message> messageList = new ArrayList<>();
                        
                        messageFlux.subscribe(
                            message -> messageList.add(message),
                            error -> future.completeExceptionally(error),
                            () -> future.complete(messageList)
                        );
                        
                        return future;
                    } else {
                        logger.debug("Processing non-streaming response");
                        // Non-streaming response already returns CompletableFuture<List<Message>>
                        return responseProcessor.processNonStreamingResponse(
                                llmResponse,
                                threadId,
                                preparedMessages,
                                llmModel,
                                processorConfig
                        );
                    }
                });
            })
            .whenComplete((responseMessages, ex) -> {
                if (ex != null) {
                    // Handle exception
                    logger.error("Error in processThread: {}", ex.getMessage(), ex);
                    
                    // Create an error message
                    Map<String, Object> errorContent = Map.of(
                        "status", "error",
                        "message", ex.getMessage()
                    );
                    
                    Message errorMessage = new Message(threadId, "status", errorContent, false, null);
                    allMessages.add(errorMessage);
                    
                    // Complete the result with the error
                    result.complete(allMessages);
                    return;
                }
                
                // Add the response messages to the accumulated messages
                allMessages.addAll(responseMessages);
                
                // Check if we need to auto-continue
                boolean shouldAutoContinue = false;
                
                // Check for finish reason in the response
                for (Message msg : responseMessages) {
                    if ("status".equals(msg.getType())) {
                        Object content = msg.getContent();
                        if (content instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> contentMap = (Map<String, Object>) content;
                            if ("finish".equals(contentMap.get("status_type"))) {
                                String finishReason = (String) contentMap.get("finish_reason");
                                
                                if ("tool_calls".equals(finishReason)) {
                                    // Only auto-continue if enabled (max > 0)
                                    if (nativeMaxAutoContinues > 0 && autoContinueCount < nativeMaxAutoContinues) {
                                        logger.info("Detected finish_reason='tool_calls', auto-continuing ({}/{})",
                                                autoContinueCount + 1, nativeMaxAutoContinues);
                                        shouldAutoContinue = true;
                                    }
                                } else if ("xml_tool_limit_reached".equals(finishReason)) {
                                    // Don't auto-continue if XML tool limit was reached
                                    logger.info("Detected finish_reason='xml_tool_limit_reached', stopping auto-continue");
                                    shouldAutoContinue = false;
                                }
                            }
                        }
                    }
                }
                
                // If we should auto-continue, recursively call processThread
                if (shouldAutoContinue) {
                    processThread(
                        result,
                        threadId,
                        workingSystemPrompt,
                        stream,
                        null, // No temporary message for auto-continue
                        llmModel,
                        llmTemperature,
                        llmMaxTokens,
                        processorConfig,
                        toolChoice,
                        nativeMaxAutoContinues,
                        maxXmlToolCalls,
                        enableThinking,
                        reasoningEffort,
                        enableContextManager,
                        addMessageCallback,
                        autoContinueCount + 1,
                        allMessages,
                        userId,
                        runId,
                        startTime
                    );
                } else {
                    // If we've reached the max auto-continues, log a warning
                    if (autoContinueCount >= nativeMaxAutoContinues && nativeMaxAutoContinues > 0) {
                        logger.warn("Reached maximum auto-continue limit ({}), stopping.", nativeMaxAutoContinues);
                        
                        // Add a message indicating we reached the limit
                        Map<String, Object> limitContent = Map.of(
                            "type", "content",
                            "content", String.format("\n[Agent reached maximum auto-continue limit of %d]", nativeMaxAutoContinues)
                        );
                        
                        Message limitMessage = new Message(threadId, "status", limitContent, false, null);
                        allMessages.add(limitMessage);
                        
                        try {
                            addMessage(threadId, "status", limitContent, false, null).get();
                        } catch (Exception e) {
                            logger.error("Error adding limit message: {}", e.getMessage(), e);
                        }
                    }
                    
                    // Complete the result with the accumulated messages
                    result.complete(allMessages);
                }
            });
    }
    
    /**
     * Convert a map representation of a tool call to a ToolCall object.
     * 
     * @param toolCallMap The map representation of the tool call
     * @return The ToolCall object, or null if conversion fails
     */
    @SuppressWarnings("unchecked")
    private ToolCall convertToToolCall(Map<String, Object> toolCallMap) {
        try {
            String id = (String) toolCallMap.get("id");
            String type = (String) toolCallMap.get("type"); // Should be "function" for native tool calls
            
            if (!"function".equals(type)) {
                logger.warn("Unexpected tool call type: {}", type);
                return null;
            }
            
            Map<String, Object> functionMap = (Map<String, Object>) toolCallMap.get("function");
            if (functionMap == null) {
                logger.warn("Tool call map is missing 'function' field");
                return null;
            }
            
            String functionName = (String) functionMap.get("name");
            String functionArguments = (String) functionMap.get("arguments"); // Arguments are a JSON string
            
            if (id == null || functionName == null || functionArguments == null) {
                logger.warn("Tool call map is missing required fields (id, function.name, function.arguments)");
                return null;
            }
            
            // Create a FunctionCall object
            ToolCall.FunctionCall functionCall = new ToolCall.FunctionCall(functionName, functionArguments);
            
            // Create the ToolCall object
            return new ToolCall(id, type, functionCall);
            
        } catch (Exception e) {
            logger.error("Error converting tool call map to ToolCall object: {}", e.getMessage(), e);
            return null;
        }
    }
}
