package com.nubian.ai.agent.service.helper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import com.nubian.ai.agentpress.model.LlmMessage;
import lombok.extern.slf4j.Slf4j;

/**
 * Helper class for loading and constructing agent system prompts.
 */
@Slf4j
public class AgentPromptHelper {
    private static final String DEFAULT_PROMPT_PATH = "prompt.txt";
    private static String cachedPromptTemplate = null;

    /**
     * Load the prompt template from resources.
     * 
     * @return The prompt template as a string
     */
    public static String loadPromptTemplate() {
        if (cachedPromptTemplate != null) {
            return cachedPromptTemplate;
        }
        
        try {
            Resource resource = new ClassPathResource(DEFAULT_PROMPT_PATH);
            if (resource.exists()) {
                cachedPromptTemplate = new String(Files.readAllBytes(Paths.get(resource.getURI())));
                log.info("Loaded prompt template from resources");
                return cachedPromptTemplate;
            } else {
                log.error("Prompt template file not found: {}", DEFAULT_PROMPT_PATH);
                return "You are a helpful assistant.";
            }
        } catch (IOException e) {
            log.error("Error loading prompt template: {}", e.getMessage(), e);
            return "You are a helpful assistant.";
        }
    }
    
    /**
     * Construct a system prompt with dynamic placeholders filled in.
     * 
     * @param additionalContext Additional context to add to the prompt
     * @return The constructed system prompt as an LlmMessage
     */
    public static LlmMessage constructSystemPrompt(Map<String, String> additionalContext) {
        String template = loadPromptTemplate();
        
        // Get current date and time
        Instant now = Instant.now();
        DateTimeFormatter formatter = DateTimeFormatter.ISO_INSTANT;
        String currentDateTime = formatter.format(now);
        
        // Apply date/time replacements
        String promptContent = template.replace("{datetime.datetime.now(datetime.timezone.utc).strftime('%Y-%m-%d')}", 
                                               now.atZone(ZoneId.of("UTC")).toLocalDate().toString());
        promptContent = promptContent.replace("{datetime.datetime.now(datetime.timezone.utc).strftime('%H:%M:%S')}", 
                                            now.atZone(ZoneId.of("UTC")).toLocalTime().toString());
        
        // Apply additional context
        if (additionalContext != null) {
            for (Map.Entry<String, String> entry : additionalContext.entrySet()) {
                promptContent = promptContent.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        
        log.debug("Constructed system prompt with current time: {}", currentDateTime);
        return new LlmMessage("system", promptContent);
    }
    
    /**
     * Construct a system prompt without additional context.
     * 
     * @return The constructed system prompt as an LlmMessage
     */
    public static LlmMessage constructSystemPrompt() {
        return constructSystemPrompt(null);
    }
}
