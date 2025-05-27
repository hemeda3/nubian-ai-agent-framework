package com.Nubian.ai.agent.service.manager;

import java.util.Map;

import com.Nubian.ai.agent.service.AgentRunnerService;
import com.Nubian.ai.agentpress.model.Message;
import com.Nubian.ai.agentpress.model.ToolResult;
import com.Nubian.ai.agentpress.service.ContextManager;
import com.Nubian.ai.agentpress.service.DBConnection;
import com.Nubian.ai.agentpress.service.ThreadManager;
import com.Nubian.ai.agentpress.sandbox.tool.ResilientFileTool;

import lombok.extern.slf4j.Slf4j;

/**
 * Manages file operations for agent runs.
 */
@Slf4j
public class FileOperationManager {

    private final AgentRunnerService agentRunnerService;
    private final ThreadManager threadManager;
    private final ContextManager contextManager;
    private final DBConnection dbConnection;

    /**
     * Initialize FileOperationManager.
     *
     * @param agentRunnerService The agent runner service
     * @param threadManager The thread manager
     * @param contextManager The context manager
     * @param dbConnection The database connection
     */
    public FileOperationManager(
            AgentRunnerService agentRunnerService,
            ThreadManager threadManager,
            ContextManager contextManager,
            DBConnection dbConnection) {
        this.agentRunnerService = agentRunnerService;
        this.threadManager = threadManager;
        this.contextManager = contextManager;
        this.dbConnection = dbConnection;
    }

    /**
     * Read the todo.md file from the sandbox.
     * 
     * @param projectId The project ID
     * @param todoPath The path to the todo.md file
     * @return The content of the todo.md file, or an empty string if not found
     */
    public String readTodoFile(String projectId, String todoPath) {
        try {
            // Create a resilient file tool to read the todo.md file
            ResilientFileTool fileTool = new ResilientFileTool(projectId);
            fileTool.setSandboxService(agentRunnerService.getSandboxService());
            fileTool.setSandboxFileService(agentRunnerService.getSandboxFileService());
            fileTool.setThreadManager(threadManager);
            fileTool.setContextManager(contextManager);
            fileTool.setDbConnection(dbConnection);
            fileTool.setWorkspaceService(agentRunnerService.getWorkspaceService());
            
            // Read the file
            ToolResult result = fileTool.readFile(todoPath).join();
            
            if (result.isSuccess()) {
                Object contentObj = result.getOutput();
                if (contentObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> contentMap = (Map<String, Object>) contentObj;
                    return (String) contentMap.get("content");
                } else if (contentObj instanceof String) {
                    return (String) contentObj;
                }
            }
            
            return ""; // Return empty string if file doesn't exist or couldn't be read
        } catch (Exception e) {
            log.warn("Error reading todo.md: {}", e.getMessage());
            return ""; // Return empty string on error
        }
    }
    
    /**
     * Add todo.md content to the thread as a user message.
     * 
     * @param threadId The thread ID
     * @param todoContent The todo.md content
     */
    public void addTodoContentToThread(String threadId, String todoContent) {
        try {
            Map<String, Object> todoMessageContent = Map.of(
                "role", "user",
                "content", "Current todo.md:\n```\n" + todoContent + "\n```"
            );
            
            Message todoMessage = new Message(threadId, "user", todoMessageContent, false, null);
            
            // Add the message to the thread history
            threadManager.addMessage(
                threadId,
                todoMessage.getType(),
                todoMessage.getContent(),
                todoMessage.isLlmMessage(),
                todoMessage.getMetadata()
            );
            
            log.debug("Added todo.md content to thread {}", threadId);
        } catch (Exception e) {
            log.error("Error adding todo.md content to thread: {}", e.getMessage());
        }
    }
    
    /**
     * Update the todo.md file based on LLM message content.
     * 
     * @param message The message from the LLM
     * @param projectId The project ID
     * @param todoPath The path to the todo.md file
     * @return True if the file was updated, false otherwise
     */
    public boolean updateTodoFileFromMessage(Message message, String projectId, String todoPath) {
        if (!"assistant".equals(message.getType())) {
            return false;
        }
        
        try {
            Object contentObj = message.getContent();
            if (contentObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> contentMap = (Map<String, Object>) contentObj;
                String textContent = (String) contentMap.get("content");
                
                if (textContent != null && textContent.contains("<todo_update>")) {
                    // Extract content between <todo_update> and </todo_update>
                    int startIndex = textContent.indexOf("<todo_update>") + "<todo_update>".length();
                    int endIndex = textContent.indexOf("</todo_update>", startIndex);
                    
                    if (endIndex != -1) {
                        String updatedTodoContent = textContent.substring(startIndex, endIndex).trim();
                        
                        // Create a resilient file tool to write the updated todo.md file
                        ResilientFileTool fileTool = new ResilientFileTool(projectId);
                        fileTool.setSandboxService(agentRunnerService.getSandboxService());
                        fileTool.setSandboxFileService(agentRunnerService.getSandboxFileService());
                        fileTool.setThreadManager(threadManager);
                        fileTool.setContextManager(contextManager);
                        fileTool.setDbConnection(dbConnection);
                        fileTool.setWorkspaceService(agentRunnerService.getWorkspaceService());
                        
                        // Write the file
                        fileTool.writeFile(todoPath, updatedTodoContent, false).join();
                        
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error updating todo.md: {}", e.getMessage());
        }
        
        return false;
    }
}
