package com.Nubian.ai.agent.service.helper;

import com.Nubian.ai.agent.model.AgentRunStatus;
import com.Nubian.ai.agentpress.model.LlmMessage;
import com.Nubian.ai.agentpress.model.Message;
import com.Nubian.ai.agentpress.model.ProcessorConfig;
import com.Nubian.ai.agentpress.service.ThreadManager;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helper class for executing the core agent run loop.
 * Handles the iteration logic and tool execution.
 */
@Slf4j
public class AgentExecutionHelper {
    private final ThreadManager threadManager;
    private final Map<String, Boolean> stopSignals = new ConcurrentHashMap<>();
    
    public AgentExecutionHelper(ThreadManager threadManager) {
        this.threadManager = threadManager;
    }
    
    /**
     * Execute one iteration of the agent run loop.
     * This implements the full agent loop logic similar to Python's agent/run.py.
     */
    public CompletableFuture<AgentLoopResult> executeIteration(
            String agentRunId,
            String threadId,
            String projectId,
            LlmMessage systemPrompt,
            LlmMessage temporaryMessage,
            String modelName,
            boolean enableThinking,
            String reasoningEffort,
            boolean stream,
            boolean enableContextManager,
            String userId, // Add userId
            String runId, // Add runId
            java.time.Instant startTime) { // Add startTime
            
        log.info("[AGENT_RUN {}] Executing iteration for thread {}", agentRunId, threadId);
        
        // Create a proper processor configuration for the LLM call
        ProcessorConfig processorConfig = new ProcessorConfig()
            .setXmlToolCalling(true)
            .setNativeToolCalling(true) // Support both XML and native tool calling
            .setExecuteTools(true)
            .setExecuteOnStream(true)
            .setToolExecutionStrategy("parallel")
            .setXmlAddingStrategy("user_message");
            
        // Determine max tokens based on model for proper context window management
        final Integer llmMaxTokens = calculateMaxTokens(modelName);
        
        // Check if we need to handle todo.md management
        CompletableFuture<Void> todoMgmtFuture = CompletableFuture.completedFuture(null);
        
        // Process any system directives that may be in todo.md
        if (getStopSignal(agentRunId)) {
            return CompletableFuture.completedFuture(
                new AgentLoopResult(List.of(), false, null, false)
            );
        }
        
        // Execute the thread run with full context
        return todoMgmtFuture.thenCompose(v -> threadManager.runThread(
            threadId,
            systemPrompt,
            stream,
            temporaryMessage,
            modelName,
            0.0f, // temperature
            llmMaxTokens,
            processorConfig,
            "auto", // tool_choice
            3,      // nativeMaxAutoContinues (allow some auto-continues for complex tool chains)
            25,     // maxXmlToolCalls
            true,   // includeXmlExamples
            enableThinking,
            reasoningEffort,
            enableContextManager,
            userId, // Pass userId
            runId, // Pass runId
            startTime // Pass startTime
        )).thenApply(messages -> processMessages(messages, agentRunId));
    }
    
    private AgentLoopResult processMessages(List<Message> messages, String agentRunId) {
        boolean shouldContinue = true;
        String terminatingTool = null;
        boolean hasError = false;
        
        for (Message msg : messages) {
            if ("assistant".equals(msg.getType())) {
                Object contentObj = msg.getContent();
                if (contentObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    String assistantText = (String) ((Map<String,Object>)contentObj).get("content");
                    if (assistantText != null) {
                        if (assistantText.contains("</ask>")) terminatingTool = "ask";
                        else if (assistantText.contains("</complete>")) terminatingTool = "complete";
                        else if (assistantText.contains("</web-browser-takeover>")) terminatingTool = "web-browser-takeover";
                    }
                }
            } else if ("status".equals(msg.getType())) {
                Object contentObj = msg.getContent();
                if (contentObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String,Object> statusContent = (Map<String,Object>)contentObj;
                    if ("error".equals(statusContent.get("status_type")) || "error".equals(statusContent.get("status"))) {
                        log.error("[AGENT_RUN {}] Error status received: {}", agentRunId, statusContent.get("message"));
                        hasError = true;
                    }
                }
            }
        }
        
        if (hasError) {
            shouldContinue = false;
        } else if (terminatingTool != null) {
            log.info("[AGENT_RUN {}] Agent used terminating tool: {}", agentRunId, terminatingTool);
            shouldContinue = false;
        }
        
        return new AgentLoopResult(messages, shouldContinue, terminatingTool, hasError);
    }
    
    public void setStopSignal(String agentRunId, boolean value) {
        stopSignals.put(agentRunId, value);
    }
    
    public boolean getStopSignal(String agentRunId) {
        return stopSignals.getOrDefault(agentRunId, false);
    }
    
    /**
     * Calculate the maximum token limit based on the model name.
     * Different models have different context window sizes.
     * 
     * @param modelName The name of the LLM model
     * @return The appropriate maximum token limit for the model
     */
    private Integer calculateMaxTokens(String modelName) {
        if (modelName.contains("sonnet") || modelName.contains("opus")) {
            return 128000; // Claude 3 Opus/Sonnet can handle large contexts
        } else if (modelName.contains("gpt-4o")) {
            return 128000; // GPT-4o can handle up to 128k tokens
        } else if (modelName.contains("gpt-4-turbo")) {
            return 128000; // GPT-4 Turbo can handle up to 128k tokens
        } else if (modelName.contains("gpt-4-vision") || modelName.contains("gpt-4-1106")) {
            return 128000; // GPT-4 Vision can handle up to 128k tokens
        } else if (modelName.contains("gpt-4")) {
            return 8192; // Standard GPT-4
        } else if (modelName.contains("gpt-3.5")) {
            return 16384; // GPT-3.5 Turbo
        } else if (modelName.contains("gemini")) {
            return 32768; // Gemini
        } else if (modelName.contains("claude")) {
            return 100000; // Default for Claude models
        } else {
            return 16384; // Default fallback
        }
    }
    
    /**
     * Result of an agent loop iteration.
     */
    public static class AgentLoopResult {
        private final List<Message> messages;
        private final boolean shouldContinue;
        private final String terminatingTool;
        private final boolean hasError;
        
        public AgentLoopResult(List<Message> messages, boolean shouldContinue, 
                              String terminatingTool, boolean hasError) {
            this.messages = messages;
            this.shouldContinue = shouldContinue;
            this.terminatingTool = terminatingTool;
            this.hasError = hasError;
        }
        
        public List<Message> getMessages() {
            return messages;
        }
        
        public boolean shouldContinue() {
            return shouldContinue;
        }
        
        public String getTerminatingTool() {
            return terminatingTool;
        }
        
        public boolean hasError() {
            return hasError;
        }
    }
}
