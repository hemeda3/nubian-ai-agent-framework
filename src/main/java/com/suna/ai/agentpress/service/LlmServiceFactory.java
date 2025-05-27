package com.Nubian.ai.agentpress.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Factory for creating OpenAI LLM service instances.
 * Simplified to only support OpenAI provider.
 */
@Service
public class LlmServiceFactory {
    private static final Logger logger = LoggerFactory.getLogger(LlmServiceFactory.class);
    
    private final OpenAgentGenericallyWrapperService openAIWrapperService;
    private final String defaultProvider;
    
    /**
     * Initialize the LLM service factory.
     * 
     * @param openAIWrapperService The OpenAI LLM service wrapper
     * @param defaultProvider The default LLM provider to use (should be "openai")
     */
    @Autowired
    public LlmServiceFactory(
            OpenAgentGenericallyWrapperService openAIWrapperService,
            @Value("${llm.provider:openai}") String defaultProvider) {
        this.openAIWrapperService = openAIWrapperService;
        this.defaultProvider = defaultProvider.toLowerCase();
        
        if (!"openai".equals(this.defaultProvider)) {
            logger.warn("Only OpenAI provider is supported. Defaulting to 'openai' instead of '{}'", this.defaultProvider);
        }
        
        logger.info("Initialized LLM service factory with OpenAI support only");
    }
    
    /**
     * Get the LLM service for the specified provider.
     * Only OpenAI is supported.
     * 
     * @param provider The LLM provider to use (only "openai" is supported)
     * @return The LLM service
     */
    public LlmService getLlmService(String provider) {
        if (provider == null) {
            provider = "openai";
        }
        
        provider = provider.toLowerCase();
        
        if (!"openai".equals(provider)) {
            logger.warn("Only OpenAI provider is supported. Using OpenAI instead of: {}", provider);
        }
        
        logger.debug("Using OpenAI LLM service");
        openAIWrapperService.setProvider("openai");
        return new LlmServiceAdapter(openAIWrapperService);
    }
    
    /**
     * Get the default LLM service (OpenAI).
     * 
     * @return The default OpenAI LLM service
     */
    public LlmService getDefaultLlmService() {
        return getLlmService("openai");
    }
}
