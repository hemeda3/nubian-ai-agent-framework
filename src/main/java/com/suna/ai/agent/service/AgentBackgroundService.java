package com.Nubian.ai.agent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.Nubian.ai.agent.model.AgentRunRequest;
import com.Nubian.ai.agent.model.AgentRunStatus;
import com.Nubian.ai.agentpress.service.ContextManager;
import com.Nubian.ai.agentpress.service.DBConnection;
import com.Nubian.ai.agentpress.service.ThreadManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for handling agent runs in the background.
 * 
 * This is the Java equivalent of the Python run_agent_background worker,
 * handling background processing, Redis state management, and RabbitMQ messaging.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentBackgroundService {

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisMessageListenerContainer redisMessageListenerContainer;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final ThreadManager threadManager;
    private final ContextManager contextManager;
    private final DBConnection dbConnection;
    private final AgentRunManager agentRunManager;
    private final AgentRunnerService agentRunnerService;
    
    @Value("${redis.key.ttl:3600}")
    private long redisKeyTtl;
    
    @Value("${redis.response.list.ttl:86400}")
    private long redisResponseListTtl;
    
    private final String instanceId = UUID.randomUUID().toString().substring(0, 8);
    private final Map<String, CompletableFuture<Void>> runningAgentTasks = new ConcurrentHashMap<>();
    private final Map<String, Boolean> stopSignals = new ConcurrentHashMap<>();

    /**
     * Listen for agent run messages from RabbitMQ.
     * 
     * @param message The message containing agent run information
     */
    @RabbitListener(queues = "agent-run-queue")
    public void receiveAgentRunMessage(String message) {
        try {
            log.info("Received agent run message: {}", message);
            Map<String, Object> messageMap = objectMapper.readValue(message, Map.class);
            
            String agentRunId = (String) messageMap.get("agent_run_id");
            String threadId = (String) messageMap.get("thread_id");
            String projectId = (String) messageMap.get("project_id");
            String modelName = (String) messageMap.get("model_name");
            Boolean enableThinking = (Boolean) messageMap.get("enable_thinking");
            String reasoningEffort = (String) messageMap.get("reasoning_effort");
            Boolean stream = (Boolean) messageMap.get("stream");
            Boolean enableContextManager = (Boolean) messageMap.get("enable_context_manager");
            
            // Start the agent run in the background
            runAgentInBackground(
                agentRunId, 
                threadId, 
                projectId, 
                modelName, 
                enableThinking, 
                reasoningEffort, 
                stream, 
                enableContextManager
            );
        } catch (Exception e) {
            log.error("Error processing agent run message: {}", e.getMessage(), e);
        }
    }

    /**
     * Run an agent in the background.
     * 
     * @param agentRunId The agent run ID
     * @param threadId The thread ID
     * @param projectId The project ID
     * @param modelName The model name
     * @param enableThinking Whether to enable thinking
     * @param reasoningEffort The reasoning effort
     * @param stream Whether to stream the response
     * @param enableContextManager Whether to enable context management
     */
    @Async
    public void runAgentInBackground(
            String agentRunId,
            String threadId,
            String projectId,
            String modelName,
            Boolean enableThinking,
            String reasoningEffort,
            Boolean stream,
            Boolean enableContextManager) {
        
        log.info("Starting background agent run: {} for thread: {} (Instance: {})",
                agentRunId, threadId, instanceId);
        log.info("🚀 Using model: {} (thinking: {}, reasoning_effort: {})",
                modelName, enableThinking, reasoningEffort);
        
        CompletableFuture<Void> runTask = new CompletableFuture<>();
        runningAgentTasks.put(agentRunId, runTask);
        
        Instant startTime = Instant.now();
        int totalResponses = 0;
        
        // Define Redis keys and channels
        final String responseListKey = "agent_run:" + agentRunId + ":responses";
        final String responseChannel = "agent_run:" + agentRunId + ":new_response";
        final String instanceControlChannel = "agent_run:" + agentRunId + ":control:" + instanceId;
        final String globalControlChannel = "agent_run:" + agentRunId + ":control";
        final String instanceActiveKey = "active_run:" + instanceId + ":" + agentRunId;
        
        // Set up stop signal listener
        stopSignals.put(agentRunId, false);
        setupStopSignalListener(agentRunId, instanceControlChannel, globalControlChannel);
        
        // Set the active run key with TTL
        redisTemplate.opsForValue().set(instanceActiveKey, "running", Duration.ofSeconds(redisKeyTtl));
        
        try {
            // Set up the thread for this agent run if needed
            String actualThreadId = threadId;
            if (actualThreadId == null || actualThreadId.isEmpty()) {
                actualThreadId = UUID.randomUUID().toString();
                log.info("Created new thread with ID: {}", actualThreadId);
                
                // Add a system message to set the context
                Map<String, Object> systemContent = new HashMap<>();
                systemContent.put("role", "system");
                systemContent.put("content", "You are a helpful assistant. Answer the user's questions concisely and accurately.");
                
                threadManager.addMessage(
                    actualThreadId, 
                    "system", 
                    systemContent, 
                    false, 
                    null
                ).join();
            }
            
            // Create the agent run request
            AgentRunRequest request = new AgentRunRequest();
            request.setModelName(modelName);
            request.setEnableThinking(enableThinking);
            request.setReasoningEffort(reasoningEffort);
            request.setStream(stream);
            request.setEnableContextManager(enableContextManager);
            
            // Execute the agent run using AgentRunnerService
            log.info("Executing agent run using AgentRunnerService");
            
            // Execute the agent run
            CompletableFuture<Void> agentRunFuture = agentRunnerService.executeAgentRun(
                agentRunId,
                actualThreadId,
                projectId,
                request,
                instanceId
            );
            
            // Wait for the agent run to complete
            agentRunFuture.join();
            
            // Get the final status
            AgentRunStatus finalStatus = agentRunnerService.getAgentRunStatus(agentRunId);
            String errorMessage = agentRunnerService.getAgentRunError(agentRunId);
            
            log.info("Agent run {} completed with status: {} (duration: {}s)",
                    agentRunId, finalStatus, Duration.between(startTime, Instant.now()).getSeconds());
            
        } catch (Exception e) {
            log.error("Error in agent run {}: {}", agentRunId, e.getMessage(), e);
            
            // Create error response
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("type", "status");
            errorResponse.put("status", "error");
            errorResponse.put("message", e.getMessage());
            
            try {
                String errorJson = objectMapper.writeValueAsString(errorResponse);
                redisTemplate.opsForList().rightPush(responseListKey, errorJson);
                redisTemplate.convertAndSend(responseChannel, "new");
                
                // Update agent run status in database
                List<Map<String, Object>> responses = new ArrayList<>();
                responses.add(errorResponse);
                updateAgentRunStatus(agentRunId, AgentRunStatus.FAILED, e.getMessage(), responses);
                
                // Publish error signal
                redisTemplate.convertAndSend(globalControlChannel, "ERROR");
                
            } catch (JsonProcessingException ex) {
                log.error("Error serializing error response: {}", ex.getMessage(), ex);
            }
        } finally {
            // Clean up
            cleanup(agentRunId, responseListKey, instanceActiveKey);
            runningAgentTasks.remove(agentRunId);
            stopSignals.remove(agentRunId);
            runTask.complete(null);
        }
    }
    
    /**
     * Set up a listener for stop signals.
     * 
     * @param agentRunId The agent run ID
     * @param instanceChannel The instance-specific control channel
     * @param globalChannel The global control channel
     */
    private void setupStopSignalListener(String agentRunId, String instanceChannel, String globalChannel) {
        MessageListener messageListener = new MessageListener() {
            @Override
            public void onMessage(Message message, byte[] pattern) {
                String channel = new String(message.getChannel());
                String content = new String(message.getBody());
                
                if (content.equals("STOP")) {
                    log.info("Received STOP signal for agent run {} on channel {}", 
                            agentRunId, channel);
                    stopSignals.put(agentRunId, true);
                    
                    // Complete the running task to abort processing
                    CompletableFuture<Void> task = runningAgentTasks.get(agentRunId);
                    if (task != null && !task.isDone()) {
                        task.complete(null);
                    }
                }
            }
        };
        
        // Register listeners for both channels
        redisMessageListenerContainer.addMessageListener(
                messageListener, 
                new ChannelTopic(instanceChannel));
        
        redisMessageListenerContainer.addMessageListener(
                messageListener, 
                new ChannelTopic(globalChannel));
    }
    
    /**
     * Clean up Redis resources.
     * 
     * @param agentRunId The agent run ID
     * @param responseListKey The response list key
     * @param instanceActiveKey The instance active key
     */
    private void cleanup(String agentRunId, String responseListKey, String instanceActiveKey) {
        // Set TTL on the response list
        redisTemplate.expire(responseListKey, Duration.ofSeconds(redisResponseListTtl));
        
        // Delete the instance active key
        redisTemplate.delete(instanceActiveKey);
        
        log.info("Cleaned up Redis resources for agent run {}", agentRunId);
    }
    
    /**
     * Update the agent run status in the database.
     * 
     * @param agentRunId The agent run ID
     * @param status The new status
     * @param error The error message (if any)
     * @param responses The responses generated by the agent
     */
    private void updateAgentRunStatus(
            String agentRunId,
            AgentRunStatus status,
            String error,
            List<Map<String, Object>> responses) {
        
        try {
            // Set completed_at timestamp for terminal states
            Instant completedAt = null;
            if (status == AgentRunStatus.COMPLETED || status == AgentRunStatus.STOPPED || 
                status == AgentRunStatus.FAILED) {
                completedAt = Instant.now();
            }
            
            // Update the agent run status in the database with completed_at timestamp
            Map<String, Object> values = new HashMap<>();
            values.put("status", status.name());
            values.put("error_message", error);
            values.put("completed_at", completedAt);

            Map<String, Object> conditions = new HashMap<>();
            conditions.put("id", agentRunId);

            dbConnection.update("agent_runs", values, conditions).join(); // Using join to ensure completion for logging
            
            log.info("Agent run {} status updated to {} and persisted to database", agentRunId, status);
            
            if (error != null) {
                log.info("Error: {}", error);
            }
            
            // Also update the status in memory through AgentRunManager
            agentRunManager.setAgentRunStatus(agentRunId, status, error);
            
        } catch (Exception e) {
            log.error("Error updating agent run status: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Stop an agent run.
     * 
     * @param agentRunId The agent run ID
     */
    public void stopAgentRun(String agentRunId) {
        log.info("Sending stop signal for agent run {}", agentRunId);
        
        // Send stop signal to all instances
        String globalControlChannel = "agent_run:" + agentRunId + ":control";
        redisTemplate.convertAndSend(globalControlChannel, "STOP");
        
        // Set local stop signal too
        stopSignals.put(agentRunId, true);
        
        // If this instance is running the agent, complete the task
        CompletableFuture<Void> task = runningAgentTasks.get(agentRunId);
        if (task != null && !task.isDone()) {
            task.complete(null);
        }
    }
    
    /**
     * Submit an agent run to the queue.
     * 
     * @param agentRunId The agent run ID
     * @param request The agent run request
     * @param threadId The thread ID
     * @param projectId The project ID (optional)
     */
    public void submitAgentRun(String agentRunId, AgentRunRequest request, String threadId, String projectId) {
        try {
            // Determine the project ID
            String resolvedProjectId = projectId;
            
            // If project ID is not provided, try to get it from the database
            if (resolvedProjectId == null || resolvedProjectId.isEmpty()) {
                if (threadId != null && !threadId.isEmpty()) {
                    try {
                        Map<String, Object> conditions = new HashMap<>();
                        conditions.put("id", threadId);
                        List<Map<String, Object>> results = dbConnection.queryForList("threads", conditions).join();
                        
                        if (results != null && !results.isEmpty() && results.get(0).get("project_id") != null) {
                            resolvedProjectId = (String) results.get(0).get("project_id");
                            log.info("Resolved project ID '{}' from thread '{}'", resolvedProjectId, threadId);
                        } else {
                            // TODO: The fallback to "default" is for development. In production,
                            // stricter error handling or a defined way to associate runs with projects/accounts might be needed.
                            log.warn("Could not find project ID for thread '{}', using default", threadId);
                            resolvedProjectId = "default";
                        }
                    } catch (Exception e) {
                        log.error("Error querying for project ID: {}", e.getMessage(), e);
                        // TODO: The fallback to "default" is for development. In production,
                        // stricter error handling or a defined way to associate runs with projects/accounts might be needed.
                        resolvedProjectId = "default";
                    }
                } else {
                    // TODO: The fallback to "default" is for development. In production,
                    // stricter error handling or a defined way to associate runs with projects/accounts might be needed.
                    resolvedProjectId = "default";
                }
            }
            
            Map<String, Object> message = new HashMap<>();
            message.put("agent_run_id", agentRunId);
            message.put("thread_id", threadId);
            message.put("project_id", resolvedProjectId);
            message.put("model_name", request.getModelName());
            message.put("enable_thinking", request.getEnableThinking());
            message.put("reasoning_effort", request.getReasoningEffort());
            message.put("stream", request.getStream());
            message.put("enable_context_manager", request.getEnableContextManager());
            
            String json = objectMapper.writeValueAsString(message);
            
            // Send to RabbitMQ queue
            rabbitTemplate.convertAndSend("agent-run-queue", json);
            
            log.info("Submitted agent run {} to queue", agentRunId);
        } catch (Exception e) {
            log.error("Error submitting agent run to queue: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to submit agent run", e);
        }
    }
}
