package com.nubian.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Service for interacting with Redis.
 * 
 * This is the Java equivalent of the Python Redis service,
 * providing methods for key-value operations, pub/sub messaging,
 * and TTL management.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final RedisMessageListenerContainer redisMessageListenerContainer;
    
    /**
     * Default TTL for Redis keys (1 hour).
     */
    public static final long DEFAULT_TTL_SECONDS = 3600;
    
    /**
     * TTL for response lists (24 hours).
     */
    public static final long RESPONSE_LIST_TTL_SECONDS = 86400;
    
    /**
     * Set a key-value pair in Redis with a TTL.
     * 
     * @param key The key
     * @param value The value
     * @param ttlSeconds The TTL in seconds
     * @return True if successful, false otherwise
     */
    public boolean set(String key, String value, long ttlSeconds) {
        try {
            redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));
            return true;
        } catch (Exception e) {
            log.error("Error setting Redis key {}: {}", key, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Set a key-value pair in Redis with the default TTL.
     * 
     * @param key The key
     * @param value The value
     * @return True if successful, false otherwise
     */
    public boolean set(String key, String value) {
        return set(key, value, DEFAULT_TTL_SECONDS);
    }
    
    /**
     * Get a value from Redis.
     * 
     * @param key The key
     * @return The value, or null if not found
     */
    public String get(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("Error getting Redis key {}: {}", key, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Delete a key from Redis.
     * 
     * @param key The key
     * @return True if successful, false otherwise
     */
    public boolean delete(String key) {
        try {
            Boolean result = redisTemplate.delete(key);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("Error deleting Redis key {}: {}", key, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Set the TTL for a key.
     * 
     * @param key The key
     * @param ttlSeconds The TTL in seconds
     * @return True if successful, false otherwise
     */
    public boolean expire(String key, long ttlSeconds) {
        try {
            Boolean result = redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            log.error("Error setting TTL for Redis key {}: {}", key, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Push a value to the right end of a list.
     * 
     * @param key The list key
     * @param value The value
     * @return The length of the list after the push
     */
    public Long rpush(String key, String value) {
        try {
            return redisTemplate.opsForList().rightPush(key, value);
        } catch (Exception e) {
            log.error("Error pushing to Redis list {}: {}", key, e.getMessage(), e);
            return -1L;
        }
    }
    
    /**
     * Get a range of values from a list.
     * 
     * @param key The list key
     * @param start The start index
     * @param end The end index
     * @return The list of values
     */
    public List<String> lrange(String key, long start, long end) {
        try {
            return redisTemplate.opsForList().range(key, start, end);
        } catch (Exception e) {
            log.error("Error getting range from Redis list {}: {}", key, e.getMessage(), e);
            return List.of();
        }
    }
    
    /**
     * Publish a message to a channel.
     * 
     * @param channel The channel
     * @param message The message
     * @return The number of clients that received the message
     */
    public Long publish(String channel, String message) {
        try {
            return redisTemplate.convertAndSend(channel, message);
        } catch (Exception e) {
            log.error("Error publishing to Redis channel {}: {}", channel, e.getMessage(), e);
            return -1L;
        }
    }
    
    /**
     * Subscribe to a channel.
     * 
     * @param channel The channel
     * @param listener The listener
     * @return True if successful, false otherwise
     */
    public boolean subscribe(String channel, org.springframework.data.redis.connection.MessageListener listener) {
        try {
            redisMessageListenerContainer.addMessageListener(listener, new ChannelTopic(channel));
            return true;
        } catch (Exception e) {
            log.error("Error subscribing to Redis channel {}: {}", channel, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Unsubscribe from a channel.
     * 
     * @param channel The channel
     * @param listener The listener
     * @return True if successful, false otherwise
     */
    public boolean unsubscribe(String channel, org.springframework.data.redis.connection.MessageListener listener) {
        try {
            redisMessageListenerContainer.removeMessageListener(listener, new ChannelTopic(channel));
            return true;
        } catch (Exception e) {
            log.error("Error unsubscribing from Redis channel {}: {}", channel, e.getMessage(), e);
            return false;
        }
    }
}
