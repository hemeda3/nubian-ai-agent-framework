package com.nubian.ai.agent.tool;

import com.nubian.ai.agentpress.annotations.ToolFunction;
import com.nubian.ai.agentpress.model.SchemaType;
import com.nubian.ai.agentpress.model.Tool;
import com.nubian.ai.agentpress.model.ToolResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Map;

/**
 * Tool for user communication and interaction.
 * 
 * This tool provides methods for asking questions, with support for
 * attachments and user takeover suggestions.
 */
@Component
@Slf4j
public class MessageTool extends Tool {

    @Autowired
    public MessageTool(ObjectMapper objectMapper) {
        super(objectMapper); // Pass the injected ObjectMapper to the superclass
    }
    
    /**
     * Ask the user a question and wait for a response.
     * 
     * @param text The question to present to the user
     * @param attachments Optional file paths or URLs to attach to the question
     * @return ToolResult indicating the question was successfully sent
     */
    @ToolFunction(
        name = "ask",
        description = "Ask user a question and wait for response. Use for: 1) Requesting clarification on ambiguous requirements, "
                + "2) Seeking confirmation before proceeding with high-impact changes, 3) Gathering additional information needed to complete a task, "
                + "4) Offering options and requesting user preference, 5) Validating assumptions when critical to task success. "
                + "IMPORTANT: Use this tool only when user input is essential to proceed. Always provide clear context and options when applicable. "
                + "Include relevant attachments when the question relates to specific files or resources.",
        schemaType = SchemaType.XML,
        xmlTagName = "ask",
        xmlExample = "<ask attachments=\"recipes/chocolate_cake.txt,photos/cake_examples.jpg\">\n"
                + "    I'm planning to bake the chocolate cake for your birthday party. The recipe mentions \"rich frosting\" but doesn't specify what type. Could you clarify your preferences? For example:\n"
                + "    1. Would you prefer buttercream or cream cheese frosting?\n"
                + "    2. Do you want any specific flavor added to the frosting (vanilla, coffee, etc.)?\n"
                + "    3. Should I add any decorative toppings like sprinkles or fruit?\n"
                + "    4. Do you have any dietary restrictions I should be aware of?\n\n"
                + "    This information will help me make sure the cake meets your expectations for the celebration.\n"
                + "</ask>"
    )
    public ToolResult ask(String text, String attachments) {
        try {
            log.info("Asking user question: {}", text);
            
            // Process attachments if provided
            List<String> attachmentList = null;
            if (attachments != null && !attachments.isEmpty()) {
                attachmentList = Arrays.asList(attachments.split(","));
                log.info("Question includes {} attachments: {}", attachmentList.size(), attachments);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("status", "Awaiting user response...");
            
            return successResponse(result);
        } catch (Exception e) {
            log.error("Error asking user: {}", e.getMessage(), e);
            return failResponse("Error asking user: " + e.getMessage());
        }
    }
    
    /**
     * Request user takeover of browser interaction.
     * 
     * @param text Instructions for the user about what actions to take
     * @param attachments Optional file paths or URLs to attach to the request
     * @return ToolResult indicating the takeover request was successfully sent
     */
    @ToolFunction(
        name = "web-browser-takeover",
        description = "Request user takeover of browser interaction. Use this tool when: 1) The page requires complex human interaction "
                + "that automated tools cannot handle, 2) Authentication or verification steps require human input, 3) The page has anti-bot "
                + "measures that prevent automated access, 4) Complex form filling or navigation is needed, 5) The page requires human "
                + "verification (CAPTCHA, etc.). IMPORTANT: This tool should be used as a last resort after web-search and crawl-webpage "
                + "have failed, and when direct browser tools are insufficient. Always provide clear context about why takeover is needed "
                + "and what actions the user should take.",
        schemaType = SchemaType.XML,
        xmlTagName = "web-browser-takeover",
        xmlExample = "<web-browser-takeover>\n"
                + "    I've encountered a CAPTCHA verification on the page. Please:\n"
                + "    1. Solve the CAPTCHA puzzle\n"
                + "    2. Let me know once you've completed it\n"
                + "    3. I'll then continue with the automated process\n\n"
                + "    If you encounter any issues or need to take additional steps, please let me know.\n"
                + "</web-browser-takeover>"
    )
    public ToolResult webBrowserTakeover(String text, String attachments) {
        try {
            log.info("Requesting browser takeover: {}", text);
            
            // Process attachments if provided
            List<String> attachmentList = null;
            if (attachments != null && !attachments.isEmpty()) {
                attachmentList = Arrays.asList(attachments.split(","));
                log.info("Takeover request includes {} attachments: {}", attachmentList.size(), attachments);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("status", "Awaiting user browser takeover...");
            
            return successResponse(result);
        } catch (Exception e) {
            log.error("Error requesting browser takeover: {}", e.getMessage(), e);
            return failResponse("Error requesting browser takeover: " + e.getMessage());
        }
    }
    
    /**
     * Indicate that the agent has completed all tasks and is entering complete state.
     * 
     * @return ToolResult indicating successful transition to complete state
     */
    @ToolFunction(
        name = "complete",
        description = "A special tool to indicate you have completed all tasks and are about to enter complete state. "
                + "Use ONLY when: 1) All tasks in todo.md are marked complete [x], 2) The user's original request has been fully addressed, "
                + "3) There are no pending actions or follow-ups required, 4) You've delivered all final outputs and results to the user. "
                + "IMPORTANT: This is the ONLY way to properly terminate execution. Never use this tool unless ALL tasks are complete and verified. "
                + "Always ensure you've provided all necessary outputs and references before using this tool.",
        schemaType = SchemaType.XML,
        xmlTagName = "complete",
        xmlExample = "<complete>\n"
                + "<!-- This tool indicates successful completion of all tasks -->\n"
                + "<!-- The system will stop execution after this tool is used -->\n"
                + "</complete>"
    )
    public ToolResult complete() {
        try {
            log.info("Agent completed all tasks");
            
            Map<String, Object> result = new HashMap<>();
            result.put("status", "complete");
            
            return successResponse(result);
        } catch (Exception e) {
            log.error("Error entering complete state: {}", e.getMessage(), e);
            return failResponse("Error entering complete state: " + e.getMessage());
        }
    }
}
