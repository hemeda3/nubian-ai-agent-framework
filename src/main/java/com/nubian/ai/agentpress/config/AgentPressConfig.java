package com.nubian.ai.agentpress.config;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nubian.ai.agentpress.database.core.DBConnectionFacade;
import com.nubian.ai.agentpress.model.ProcessorConfig;
import com.nubian.ai.agentpress.service.ContextManager;
import com.nubian.ai.agentpress.service.DBConnection;
import com.nubian.ai.agentpress.service.DefaultLlmService;
import com.nubian.ai.agentpress.service.LlmServiceFactory;
import com.nubian.ai.agentpress.service.OpenAgentGenericallyWrapperService;
import com.nubian.ai.agentpress.service.ResponseProcessor;
import com.nubian.ai.agentpress.service.ThreadManager;
import com.nubian.ai.agentpress.service.ToolRegistry;

/**
 * Configuration for AgentPress components.
 */
@Configuration
@ConfigurationProperties(prefix = "agentpress")
public class AgentPressConfig {
    // Configuration properties
    private String defaultModel = "gpt-3.5-turbo";
    private boolean enableThinking = true;
    private String reasoningEffort = "medium";
    private int maxTokens = 1000;
    private float temperature = 0.7f;
    
    // Getters and setters for properties
    public String getDefaultModel() {
        return defaultModel;
    }
    
    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
    }
    
    public boolean isEnableThinking() {
        return enableThinking;
    }
    
    public void setEnableThinking(boolean enableThinking) {
        this.enableThinking = enableThinking;
    }
    
    public String getReasoningEffort() {
        return reasoningEffort;
    }
    
    public void setReasoningEffort(String reasoningEffort) {
        this.reasoningEffort = reasoningEffort;
    }
    
    public int getMaxTokens() {
        return maxTokens;
    }
    
    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }
    
    public float getTemperature() {
        return temperature;
    }
    
    public void setTemperature(float temperature) {
        this.temperature = temperature;
    }
    
    /**
     * Create an ObjectMapper bean.
     * 
     * @return The ObjectMapper
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
    
    /**
     * Create a ToolRegistry bean.
     * 
     * @param objectMapper The ObjectMapper for JSON serialization/deserialization
     * @return The ToolRegistry
     */
    @Bean
    public ToolRegistry toolRegistry(ObjectMapper objectMapper) {
        return new ToolRegistry(objectMapper);
    }
    
    /**
     * Create a DBConnection bean.
     * 
     * @param dbConnectionFacade The database connection facade
     * @return The DBConnection
     */
    @Bean
    public DBConnection dbConnection(DBConnectionFacade dbConnectionFacade) {
        return new DBConnection(dbConnectionFacade);
    }
    
    /**
     * Create a DefaultLlmService bean.
     * 
     * @param openAgentGenricllmWrapperService The OpenAI LLM service
     * @return The DefaultLlmService
     */
    @Bean
    public DefaultLlmService defaultLlmService(OpenAgentGenericallyWrapperService openAgentGenricllmWrapperService) {
        return new DefaultLlmService(openAgentGenricllmWrapperService);
    }
    
    /**
     * Create a ProcessorConfig bean with default settings.
     * 
     * @return The ProcessorConfig
     */
    @Bean
    public ProcessorConfig processorConfig() {
        return new ProcessorConfig()
                .setXmlToolCalling(true)
                .setNativeToolCalling(true)
                .setExecuteTools(true)
                .setExecuteOnStream(false)
                .setToolExecutionStrategy("sequential")
                .setXmlAddingStrategy("assistant_message")
                .setMaxXmlToolCalls(0);
    }
    
    /**
     * Create a ResponseProcessor bean.
     * 
     * @param toolRegistry The tool registry
     * @param objectMapper The object mapper
     * @return The ResponseProcessor
     */
    @Bean
    public ResponseProcessor responseProcessor(
            ToolRegistry toolRegistry,
            ObjectMapper objectMapper) {
        return new ResponseProcessor(
                toolRegistry,
                (threadId, message) -> {
                    // Create a wrapper that adapts the Message parameter to the expected signature
                    String type = message.getType();
                    Object content = message.getContent();
                    boolean isLlmMessage = message.isLlmMessage();
                    Map<String, Object> metadata = message.getMetadata();
                    
                    // Get the ThreadManager bean from the context
                    ThreadManager threadManager = applicationContext.getBean(ThreadManager.class);
                    return threadManager.addMessage(threadId, type, content, isLlmMessage, metadata);
                },
                objectMapper);
    }
    
    // Add ApplicationContext for bean lookup
    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;
    
    /**
     * Create a ContextManager bean.
     * 
     * @param dbConnection The database connection
     * @param llmService The LLM service
     * @param objectMapper The object mapper
     * @return The ContextManager
     */
    @Bean
    public ContextManager contextManager(
            DBConnection dbConnection,
            DefaultLlmService llmService,
            ObjectMapper objectMapper) {
        return new ContextManager(dbConnection, llmService, objectMapper);
    }
    
    /**
     * Create a ThreadManager bean.
     * 
     * @param dbConnection The database connection
     * @param toolRegistry The tool registry
     * @param responseProcessor The response processor
     * @param contextManager The context manager
     * @param llmServiceFactory The LLM service factory
     * @param objectMapper The object mapper
     * @return The ThreadManager
     */
    @Bean
    public ThreadManager threadManager(
            DBConnection dbConnection,
            ToolRegistry toolRegistry,
            ResponseProcessor responseProcessor,
            ContextManager contextManager,
            LlmServiceFactory llmServiceFactory,
            ObjectMapper objectMapper) {
        return new ThreadManager(
                dbConnection,
                toolRegistry,
                responseProcessor,
                contextManager,
                llmServiceFactory,
                objectMapper);
    }
}
