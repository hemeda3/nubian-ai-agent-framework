package com.Nubian.ai.agent.service.helper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.Nubian.ai.agent.model.AgentRunStatus;
import com.Nubian.ai.agentpress.model.Message;
import com.Nubian.ai.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Helper class for Redis operations in agent runs.
 * Handles publishing messages, status updates, and setting up listeners.
 */
@Slf4j
@RequiredArgsConstructor
public class AgentRedisHelper {
    private final RedisService redisService;
    private final RedisMessageListenerContainer listenerContainer;
    private final ObjectMapper objectMapper;
    
    // Map to keep track of active listeners for streaming
    private final Map<String, MessageListener> streamListeners = new ConcurrentHashMap<>();
    
    /**
     * Setup a listener for stop signals.
     *
     * @param agentRunId The agent run ID
     * @param instanceId The instance ID
     * @param callback Callback to execute when a stop signal is received
     */
    public void setupStopSignalListener(String agentRunId, String instanceId, Consumer<String> callback) {
        String instanceChannel = "agent_run:" + agentRunId + ":control:" + instanceId;
        String globalChannel = "agent_run:" + agentRunId + ":control";
        
        MessageListener messageListener = (message, pattern) -> {
            String channel = new String(message.getChannel());
            String content = new String(message.getBody());
            
            if (content.equals("STOP")) {
                log.info("Received STOP signal for agent run {} on channel {}", 
                        agentRunId, channel);
                callback.accept(agentRunId);
            }
        };
        
        // Register listeners for both channels
        listenerContainer.addMessageListener(messageListener, new ChannelTopic(instanceChannel));
        listenerContainer.addMessageListener(messageListener, new ChannelTopic(globalChannel));
        
        log.debug("Stop signal listeners set up for agent run {}", agentRunId);
    }
    
    // Map to track the last processed message index for each client subscription
    private final Map<String, Integer> lastProcessedIndices = new ConcurrentHashMap<>();

    /**
     * Subscribe to the response stream for a specific agent run.
     * This implementation efficiently tracks and sends only new messages to clients.
     *
     * @param agentRunId The agent run ID
     * @param callback Callback to execute when a new message is published
     */
    public void subscribeToRunStream(String agentRunId, Consumer<String> callback) {
        String responseChannel = "agent_run:" + agentRunId + ":new_response";
        String subscriptionId = agentRunId + ":" + System.currentTimeMillis();
        
        // Initialize this client's last processed index
        // Start by sending all existing messages once, then track new ones
        List<Map<String, Object>> existingMessages = getResponsesFromRedis(agentRunId);
        int startingIndex = existingMessages.size();
        lastProcessedIndices.put(subscriptionId, startingIndex);
        
        // First, send all existing messages to bring the client up to date
        for (Map<String, Object> message : existingMessages) {
            try {
                callback.accept(objectMapper.writeValueAsString(message));
            } catch (JsonProcessingException e) {
                log.error("Error serializing existing message for stream callback: {}", e.getMessage(), e);
            }
        }
        
        // Now set up the listener for new messages
        MessageListener messageListener = (message, pattern) -> {
            String content = new String(message.getBody());
            
            // When a "new" signal is received, retrieve only messages we haven't sent yet
            if (content.equals("new")) {
                List<Map<String, Object>> allMessages = getResponsesFromRedis(agentRunId);
                int lastProcessedIndex = lastProcessedIndices.getOrDefault(subscriptionId, 0);
                
                // Only process messages with indices greater than what we've seen before
                for (int i = lastProcessedIndex; i < allMessages.size(); i++) {
                    try {
                        callback.accept(objectMapper.writeValueAsString(allMessages.get(i)));
                    } catch (JsonProcessingException e) {
                        log.error("Error serializing new message for stream callback: {}", e.getMessage(), e);
                    }
                }
                
                // Update the last processed index
                if (allMessages.size() > lastProcessedIndex) {
                    lastProcessedIndices.put(subscriptionId, allMessages.size());
                }
            }
        };
        
        listenerContainer.addMessageListener(messageListener, new ChannelTopic(responseChannel));
        streamListeners.put(subscriptionId, messageListener);
        
        log.info("Subscribed to response stream for agent run {} with subscription ID {}", 
                agentRunId, subscriptionId);
    }
    
    /**
     * Unsubscribe from the response stream for a specific agent run.
     *
     * @param agentRunId The agent run ID
     */
    public void unsubscribeFromRunStream(String agentRunId) {
        MessageListener listener = streamListeners.remove(agentRunId);
        if (listener != null) {
            listenerContainer.removeMessageListener(listener);
            log.info("Unsubscribed from response stream for agent run {}", agentRunId);
        } else {
            log.warn("No active stream listener found for agent run {}", agentRunId);
        }
    }
    
    /**
     * Update the agent run status in Redis.
     *
     * @param agentRunId The agent run ID
     * @param status The new status
     * @param errorMessage Optional error message
     * @param responses The responses generated by the agent
     */
    public void updateAgentRunStatus(
            String agentRunId, 
            AgentRunStatus status, 
            String errorMessage, 
            List<Map<String, Object>> responses) {
            
        String controlChannel = "agent_run:" + agentRunId + ":control";
        String signal = null;
        
        switch (status) {
            case COMPLETED:
                signal = "END_STREAM";
                break;
            case FAILED:
                signal = "ERROR";
                break;
            case STOPPED:
                signal = "STOP";
                break;
            default:
                // No signal for other statuses
                break;
        }
        
        // Handle special case for when a tool like 'ask' is used
        // Since we don't have a PAUSED status in the enum, we'll handle it conditionally
        if (signal == null && errorMessage != null && 
            (errorMessage.contains("ask") || errorMessage.contains("Awaiting user input"))) {
            signal = "PAUSE";
            log.info("Detected pause condition for agent run {}", agentRunId);
        }
        
        if (signal != null) {
            redisService.publish(controlChannel, signal);
            log.info("Published signal {} to channel {}", signal, controlChannel);
        }
        
        log.info("Updated agent run {} status to {} in Redis", agentRunId, status);
    }
    
    /**
     * Publish a message to the agent run's response stream.
     *
     * @param agentRunId The agent run ID
     * @param message The message to publish
     */
    public void publishMessageToRedisStream(String agentRunId, Message message) {
        try {
            String responseListKey = "agent_run:" + agentRunId + ":responses";
            String responseChannel = "agent_run:" + agentRunId + ":new_response";
            
            String messageJson = objectMapper.writeValueAsString(message);
            redisService.rpush(responseListKey, messageJson);
            redisService.publish(responseChannel, "new");
            
            log.debug("Published message to Redis stream for agent run {}", agentRunId);
        } catch (JsonProcessingException e) {
            log.error("Error serializing message for Redis: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Create a status message.
     *
     * @param status The status
     * @param messageText The message text
     * @return The status message
     */
    public Map<String, Object> createStatusMessage(String status, String messageText) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "status");
        message.put("status", status);
        message.put("message", messageText);
        return message;
    }
    
    /**
     * Get all responses from Redis for an agent run.
     *
     * @param agentRunId The agent run ID
     * @return The list of responses
     */
    public List<Map<String, Object>> getResponsesFromRedis(String agentRunId) {
        String responseListKey = "agent_run:" + agentRunId + ":responses";
        List<String> responseJsons = redisService.lrange(responseListKey, 0, -1);
        
        return responseJsons.stream()
                .map(json -> {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> response = objectMapper.readValue(json, Map.class);
                        return response;
                    } catch (JsonProcessingException e) {
                        log.error("Error deserializing response from Redis: {}", e.getMessage(), e);
                        return new HashMap<String, Object>();
                    }
                })
                .toList();
    }
    
    /**
     * Clean up Redis resources for an agent run.
     *
     * @param agentRunId The agent run ID
     * @param instanceId The instance ID
     * @param responseListKey The response list key
     */
    public void cleanupRedisForRun(String agentRunId, String instanceId, String responseListKey) {
        // Set TTL on the response list
        redisService.expire(responseListKey, RedisService.RESPONSE_LIST_TTL_SECONDS);
        
        // Delete the instance active key
        String instanceActiveKey = "active_run:" + instanceId + ":" + agentRunId;
        redisService.delete(instanceActiveKey);
        
        log.info("Cleaned up Redis resources for agent run {}", agentRunId);
    }
}
