package com.Nubian.ai.agent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.Nubian.ai.agent.service.helper.AgentRedisHelper;
import com.Nubian.ai.service.RedisService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Configuration for agent helper components.
 */
@Configuration
public class AgentHelperConfig {

    /**
     * Create an AgentRedisHelper bean.
     *
     * @param redisTemplate The Redis template
     * @param redisMessageListenerContainer The Redis message listener container
     * @param objectMapper The object mapper
     * @return The AgentRedisHelper
     */
    @Bean
    public AgentRedisHelper agentRedisHelper(
            RedisTemplate<String, String> redisTemplate,
            RedisMessageListenerContainer redisMessageListenerContainer,
            ObjectMapper objectMapper) {
        
        RedisService redisService = new RedisService(redisTemplate, redisMessageListenerContainer);
        return new AgentRedisHelper(redisService, redisMessageListenerContainer, objectMapper);
    }
}
