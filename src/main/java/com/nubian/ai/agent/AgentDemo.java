package com.nubian.ai.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

import com.nubian.ai.agent.config.AgentConfig;
import com.nubian.ai.agent.model.AgentRunRequest;
import com.nubian.ai.agent.service.AgentRunManager;
import com.nubian.ai.agent.tool.MessageTool;
import com.nubian.ai.agent.tool.DataProviderTool;

import java.util.UUID;

/**
 * Demo class for the Agent implementation.
 * 
 * This class demonstrates how to use the Agent implementation
 * that's built on top of the AgentPress framework.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.nubian.ai.agent", "com.nubian.ai.agentpress"})
public class AgentDemo {
    // Auto-run flag to control whether the demo runs on startup
    private static final boolean AUTO_RUN_DEMO = false;
    private static final Logger logger = LoggerFactory.getLogger(AgentDemo.class);
    
    /**
     * Command line runner to demonstrate the Agent implementation.
     * 
     * @param agentRunManager The agent run manager
     * @param agentConfig The agent configuration
     * @param messageTool The message tool
     * @param dataProviderTool The data provider tool
     * @return The command line runner
     */
    @Bean
    public CommandLineRunner demo(
            AgentRunManager agentRunManager,
            AgentConfig agentConfig,
            MessageTool messageTool,
            DataProviderTool dataProviderTool) {
        
        return args -> {
            if (!AUTO_RUN_DEMO) {
                logger.info("Agent demo disabled - set AUTO_RUN_DEMO to true to enable automatic startup");
                return;
            }
            
            try {
                logger.info("Starting Agent demo...");
                
                // Create an agent run request
                AgentRunRequest request = new AgentRunRequest();
                request.setModelName(agentConfig.getDefaultModel());
                request.setEnableThinking(true);
                request.setReasoningEffort("medium");
                request.setStream(true);
                request.setEnableContextManager(true);
                
                // Generate a unique ID for this agent run
                String agentRunId = UUID.randomUUID().toString();
                
                // Start the agent run
                agentRunManager.startAgentRun(agentRunId, request);
                logger.info("Started agent run with ID: {}", agentRunId);
                
                // In a real application, you would need to wait for the agent to complete
                // and retrieve results through an event listener or message queue
                
                // For demo purposes, we'll just wait a bit
                Thread.sleep(3000);
                
                // Get the current status
                logger.info("Agent run status: {}", agentRunManager.getAgentRunStatus(agentRunId));
                
                // Stop the agent run
                agentRunManager.stopAgentRun(agentRunId);
                logger.info("Stopped agent run: {}", agentRunId);
                
                // Final status
                logger.info("Final agent run status: {}", agentRunManager.getAgentRunStatus(agentRunId));
                
                logger.info("Agent demo completed");
            } catch (Exception e) {
                logger.error("Error running agent demo: {}", e.getMessage(), e);
            }
        };
    }
}
