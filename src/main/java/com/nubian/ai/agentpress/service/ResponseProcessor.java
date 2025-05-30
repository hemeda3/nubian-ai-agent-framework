package com.nubian.ai.agentpress.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import reactor.core.publisher.Flux;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nubian.ai.agentpress.model.LlmResponse;
import com.nubian.ai.agentpress.model.LlmMessage;
import com.nubian.ai.agentpress.model.Message;
import com.nubian.ai.agentpress.model.ProcessorConfig;
import com.nubian.ai.agentpress.model.ToolCall;
import com.nubian.ai.agentpress.model.ToolExecutionContext;
import com.nubian.ai.agentpress.model.ToolResult;

/**
 * Processes LLM responses, extracting and executing tool calls.
 */
@Service
public class ResponseProcessor {
    private static final Logger logger = LoggerFactory.getLogger(ResponseProcessor.class);
    
    private final ToolRegistry toolRegistry;
    private final BiFunction<String, Message, CompletableFuture<Message>> addMessageCallback;
    private final ObjectMapper objectMapper;
    
    /**
     * Create a new response processor.
     * 
     * @param toolRegistry Registry of available tools
     * @param addMessageCallback Callback function to add messages to the thread
     * @param objectMapper Object mapper for JSON serialization/deserialization
     */
    @Autowired
    public ResponseProcessor(
            ToolRegistry toolRegistry,
            BiFunction<String, Message, CompletableFuture<Message>> addMessageCallback,
            ObjectMapper objectMapper) {
        this.toolRegistry = toolRegistry;
        this.addMessageCallback = addMessageCallback;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Process a streaming LLM response, handling tool calls and execution.
     * 
     * @param llmResponse The streaming response from the LLM
     * @param threadId The ID of the conversation thread
     * @param promptMessages The messages sent to the LLM (the prompt)
     * @param llmModel The name of the LLM model used
     * @param config Configuration for parsing and execution
     * @return A Flux that emits processed messages as they become available
     */
    public Flux<Message> processStreamingResponse(
            LlmResponse llmResponse,
            String threadId,
            List<LlmMessage> promptMessages,
            String llmModel,
            ProcessorConfig config) {
        
        logger.info("Processing streaming response for thread {}", threadId);
        logger.info("Streaming Config: XML={}, Native={}, Execute on stream={}, Strategy={}",
                config.isXmlToolCalling(), config.isNativeToolCalling(),
                config.isExecuteOnStream(), config.getToolExecutionStrategy());
        
        // Validate configuration
        config.validate();
        
        String threadRunId = UUID.randomUUID().toString();
        final AtomicReference<String> accumulatedContent = new AtomicReference<>("");
        final Map<Integer, Map<String, Object>> toolCallsBuffer = new ConcurrentHashMap<>();
        final AtomicReference<String> currentXmlContent = new AtomicReference<>("");
        final List<String> xmlChunksBuffer = new ArrayList<>();
        final List<Map<String, Object>> pendingToolExecutions = new ArrayList<>();
        final List<Integer> yieldedToolIndices = new ArrayList<>();
        final AtomicInteger toolIndex = new AtomicInteger(0);
        final AtomicInteger xmlToolCallCount = new AtomicInteger(0);
        final AtomicReference<String> finishReason = new AtomicReference<>(null);
        final AtomicReference<Message> lastAssistantMessageObject = new AtomicReference<>(null);
        final Map<Integer, Message> toolResultMessageObjects = new HashMap<>();
        
        return Flux.create(sink -> {
            try {
                // --- Emit and Save Start Events ---
                Map<String, Object> startContent = Map.of("status_type", "thread_run_start", "thread_run_id", threadRunId);
                Message startMsg = new Message(threadId, "status", startContent, false, Map.of("thread_run_id", threadRunId));
                sink.next(startMsg);
                addMessageCallback.apply(threadId, startMsg).get();
                
                Map<String, Object> assistStartContent = Map.of("status_type", "assistant_response_start");
                Message assistStartMsg = new Message(threadId, "status", assistStartContent, false, Map.of("thread_run_id", threadRunId));
                sink.next(assistStartMsg);
                addMessageCallback.apply(threadId, assistStartMsg).get();
                
                // --- Process Content ---
                // In a real implementation, we would process the streaming response chunk by chunk
                // For simplicity, we'll just process the final response
                
                // Extract content from response
                Object content = llmResponse.getContent(); // Content can be String or List<Object>
                accumulatedContent.set((content instanceof String) ? (String) content : "");
                
                List<ToolCall> nativeToolCalls = llmResponse.getToolCalls();
                
                // --- Process XML Tool Calls ---
                if (config.isXmlToolCalling() && !(config.getMaxXmlToolCalls() > 0 && xmlToolCallCount.get() >= config.getMaxXmlToolCalls())) {
                    String contentStringForXml = "";
                    if (content instanceof String) {
                        contentStringForXml = (String) content;
                    } else if (content instanceof List) {
                        // If multi-modal, extract text parts for XML parsing
                        StringBuilder sb = new StringBuilder();
                        for (Object part : (List<?>) content) {
                            if (part instanceof Map) {
                                Map<String, Object> partMap = (Map<String, Object>) part;
                                if ("text".equals(partMap.get("type")) && partMap.get("text") instanceof String) {
                                    sb.append(partMap.get("text"));
                                }
                            }
                        }
                        contentStringForXml = sb.toString();
                    }

                    List<String> xmlChunks = extractXmlChunks(contentStringForXml);
                    for (String xmlChunk : xmlChunks) {
                        currentXmlContent.set(currentXmlContent.get().replace(xmlChunk, ""));
                        xmlChunksBuffer.add(xmlChunk);
                        
                        Map<String, Object> result = parseXmlToolCall(xmlChunk);
                        if (result != null) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> toolCall = (Map<String, Object>) result.get("tool_call");
                            @SuppressWarnings("unchecked")
                            Map<String, Object> parsingDetails = (Map<String, Object>) result.get("parsing_details");
                            
                            xmlToolCallCount.incrementAndGet();
                            String currentAssistantId = lastAssistantMessageObject.get() != null ? 
                                    lastAssistantMessageObject.get().getMessageId() : null;
                            
                            ToolExecutionContext context = createToolContext(toolCall, toolIndex.get(), currentAssistantId, parsingDetails);
                            
                            if (config.isExecuteTools() && config.isExecuteOnStream()) {
                                // Emit and Save tool_started status
                                Message startedMsg = yieldAndSaveToolStarted(context, threadId, threadRunId);
                                sink.next(startedMsg);
                                yieldedToolIndices.add(toolIndex.get());
                                
                                CompletableFuture<ToolResult> executionTask = executeToolAsync(toolCall);
                                pendingToolExecutions.add(Map.of(
                                    "task", executionTask,
                                    "tool_call", toolCall,
                                    "tool_index", toolIndex.get(),
                                    "context", context
                                ));
                                toolIndex.incrementAndGet();
                            }
                            
                            if (config.getMaxXmlToolCalls() > 0 && xmlToolCallCount.get() >= config.getMaxXmlToolCalls()) {
                                logger.debug("Reached XML tool call limit ({})", config.getMaxXmlToolCalls());
                                finishReason.set("xml_tool_limit_reached");
                                break;
                            }
                        }
                    }
                }
                
                // --- Process Native Tool Calls (if any) ---
                if (config.isNativeToolCalling() && nativeToolCalls != null && !nativeToolCalls.isEmpty()) {
                    for (ToolCall nativeToolCall : nativeToolCalls) {
                        Map<String, Object> toolCallMap = new HashMap<>();
                        toolCallMap.put("id", nativeToolCall.getId());
                        toolCallMap.put("type", nativeToolCall.getType());
                        
                        Map<String, Object> functionMap = new HashMap<>();
                        functionMap.put("name", nativeToolCall.getFunction().getName());
                        functionMap.put("arguments", nativeToolCall.getFunction().getArguments());
                        toolCallMap.put("function", functionMap);
                        
                        toolCallsBuffer.put(toolIndex.get(), toolCallMap); // Add to buffer for final assistant message
                        
                        String currentAssistantId = lastAssistantMessageObject.get() != null ? 
                                lastAssistantMessageObject.get().getMessageId() : null;
                        
                        ToolExecutionContext context = createToolContext(toolCallMap, toolIndex.get(), currentAssistantId, null); // No parsing details for native
                        
                        if (config.isExecuteTools() && config.isExecuteOnStream()) {
                            Message startedMsg = yieldAndSaveToolStarted(context, threadId, threadRunId);
                            sink.next(startedMsg);
                            yieldedToolIndices.add(toolIndex.get());
                            
                            CompletableFuture<ToolResult> executionTask = executeToolAsync(toolCallMap);
                            pendingToolExecutions.add(Map.of(
                                "task", executionTask,
                                "tool_call", toolCallMap,
                                "tool_index", toolIndex.get(),
                                "context", context
                            ));
                            toolIndex.incrementAndGet();
                        }
                    }
                }

                // --- Emit and Save Final Assistant Message ---
                if (content != null) {
                    // Truncate accumulated_content if limit reached (only if content is String)
                    if (content instanceof String && config.getMaxXmlToolCalls() > 0 && xmlToolCallCount.get() >= config.getMaxXmlToolCalls() && !xmlChunksBuffer.isEmpty()) {
                        String lastXmlChunk = xmlChunksBuffer.get(xmlChunksBuffer.size() - 1);
                        int lastChunkEndPos = accumulatedContent.get().indexOf(lastXmlChunk) + lastXmlChunk.length();
                        if (lastChunkEndPos > 0) {
                            accumulatedContent.set(accumulatedContent.get().substring(0, lastChunkEndPos));
                        }
                    }
                    
                    Map<String, Object> messageData = new HashMap<>();
                    messageData.put("role", "assistant");
                    messageData.put("content", content); // Use original content (String or List)
                    messageData.put("tool_calls", toolCallsBuffer.isEmpty() ? null : new ArrayList<>(toolCallsBuffer.values()));
                    
                    Message assistantMessage = new Message(threadId, "assistant", messageData, true, Map.of("thread_run_id", threadRunId));
                    lastAssistantMessageObject.set(assistantMessage);
                    sink.next(assistantMessage);
                    addMessageCallback.apply(threadId, assistantMessage).get();
                }
                
                // --- Process All Tool Results Now ---
                if (config.isExecuteTools()) {
                    // Process pending tool executions (from both XML and Native)
                    for (Map<String, Object> execution : pendingToolExecutions) {
                        @SuppressWarnings("unchecked")
                        CompletableFuture<ToolResult> task = (CompletableFuture<ToolResult>) execution.get("task");
                        try {
                            ToolResult result = task.get();
                            
                            @SuppressWarnings("unchecked")
                            Map<String, Object> toolCall = (Map<String, Object>) execution.get("tool_call");
                            int toolIdx = (Integer) execution.get("tool_index");
                            @SuppressWarnings("unchecked")
                            ToolExecutionContext context = (ToolExecutionContext) execution.get("context");
                            
                            context.setResult(result);
                            
                            // Emit and Save tool completed/failed status
                            Message toolResultMessage = addToolResult(
                                threadId, toolCall, result, config.getXmlAddingStrategy(),
                                lastAssistantMessageObject.get() != null ? lastAssistantMessageObject.get().getMessageId() : null,
                                context.getParsingDetails()
                            );
                            
                            Message completedMsg = yieldAndSaveToolCompleted(
                                context, toolResultMessage != null ? toolResultMessage.getMessageId() : null,
                                threadId, threadRunId
                            );
                            
                            sink.next(toolResultMessage);
                            sink.next(completedMsg);
                            toolResultMessageObjects.put(toolIdx, toolResultMessage);
                        } catch (InterruptedException | ExecutionException e) {
                            logger.error("Error executing tool: {}", e.getMessage(), e);
                            // Emit error status for this specific tool execution
                            Message errorMsg = yieldAndSaveToolError(
                                (ToolExecutionContext) execution.get("context"), threadId, threadRunId
                            );
                            sink.next(errorMsg);
                        }
                    }
                }
                
                // --- Emit and Save Final Status ---
                if (finishReason.get() != null) {
                    Map<String, Object> finishContent = Map.of("status_type", "finish", "finish_reason", finishReason.get());
                    Message finishMsg = new Message(threadId, "status", finishContent, false, Map.of("thread_run_id", threadRunId));
                    sink.next(finishMsg);
                    addMessageCallback.apply(threadId, finishMsg).get();
                }
                
                // --- Emit and Save Thread Run End Status ---
                Map<String, Object> endContent = Map.of("status_type", "thread_run_end");
                Message endMsg = new Message(threadId, "status", endContent, false, Map.of("thread_run_id", threadRunId));
                sink.next(endMsg);
                addMessageCallback.apply(threadId, endMsg).get();
                
                sink.complete(); // Signal completion of the Flux
                
            } catch (Exception e) {
                logger.error("Error processing stream: {}", e.getMessage(), e);
                
                // Emit and Save error status message
                Map<String, Object> errContent = Map.of(
                    "role", "system", "status_type", "error", "message", e.getMessage()
                );
                
                Message errMsg = new Message(threadId, "status", errContent, false, 
                        threadRunId != null ? Map.of("thread_run_id", threadRunId) : null);
                
                try {
                    sink.next(errMsg);
                    addMessageCallback.apply(threadId, errMsg).get();
                } catch (InterruptedException | ExecutionException ex) {
                    logger.error("Error adding error message: {}", ex.getMessage(), ex);
                }
                sink.error(e); // Signal error in the Flux
            }
        });
    }
    
    /**
     * Process a non-streaming LLM response, handling tool calls and execution.
     * 
     * @param llmResponse The response from the LLM
     * @param threadId The ID of the conversation thread
     * @param promptMessages The messages sent to the LLM (the prompt)
     * @param llmModel The name of the LLM model used
     * @param config Configuration for parsing and execution
     * @return A CompletableFuture that completes with the processed response
     */
    public CompletableFuture<List<Message>> processNonStreamingResponse(
            LlmResponse llmResponse,
            String threadId,
            List<LlmMessage> promptMessages,
            String llmModel,
            ProcessorConfig config) {
        
        logger.info("Processing non-streaming response for thread {}", threadId);
        
        // This is similar to processStreamingResponse but simpler since we have the full response
        // For brevity, we'll implement a simplified version
        
        String threadRunId = UUID.randomUUID().toString();
        List<Message> resultMessages = new ArrayList<>();
        
        try {
            // --- Save and Add Start Events ---
            Map<String, Object> startContent = Map.of("status_type", "thread_run_start", "thread_run_id", threadRunId);
            Message startMsg = new Message(threadId, "status", startContent, false, Map.of("thread_run_id", threadRunId));
            resultMessages.add(startMsg);
            addMessageCallback.apply(threadId, startMsg).get();
            
            // --- Extract Content and Tool Calls ---
            Object content = llmResponse.getContent(); // Content can be String or List<Object>
            List<ToolCall> nativeToolCalls = llmResponse.getToolCalls();
            
            List<Map<String, Object>> allToolData = new ArrayList<>();
            
            // Process XML tool calls if enabled
            if (config.isXmlToolCalling()) {
                String contentString = "";
                if (content instanceof String) {
                    contentString = (String) content;
                } else if (content instanceof List) {
                    // If multi-modal, extract text parts for XML parsing
                    StringBuilder sb = new StringBuilder();
                    for (Object part : (List<?>) content) {
                        if (part instanceof Map) {
                            Map<String, Object> partMap = (Map<String, Object>) part;
                            if ("text".equals(partMap.get("type")) && partMap.get("text") instanceof String) {
                                sb.append(partMap.get("text"));
                            }
                        }
                    }
                    contentString = sb.toString();
                }

                List<Map<String, Object>> parsedXmlData = parseXmlToolCalls(contentString);
                
                // Apply max tool call limit if set
                if (config.getMaxXmlToolCalls() > 0 && parsedXmlData.size() > config.getMaxXmlToolCalls()) {
                    parsedXmlData = parsedXmlData.subList(0, config.getMaxXmlToolCalls());
                    // Truncate content if limit exceeded (only if content is String)
                    if (content instanceof String) {
                        List<String> xmlChunks = extractXmlChunks((String) content);
                        if (!xmlChunks.isEmpty() && xmlChunks.size() > config.getMaxXmlToolCalls()) {
                            String lastChunk = xmlChunks.get(config.getMaxXmlToolCalls() - 1);
                            int lastChunkPos = ((String) content).indexOf(lastChunk);
                            if (lastChunkPos >= 0) {
                                content = ((String) content).substring(0, lastChunkPos + lastChunk.length());
                            }
                        }
                    }
                }
                allToolData.addAll(parsedXmlData);
            }

            // Process Native tool calls if enabled
            List<Map<String, Object>> nativeToolCallData = new ArrayList<>();
            if (config.isNativeToolCalling() && nativeToolCalls != null && !nativeToolCalls.isEmpty()) {
                for (ToolCall toolCall : nativeToolCalls) {
                    Map<String, Object> toolCallMap = new HashMap<>();
                    toolCallMap.put("id", toolCall.getId());
                    toolCallMap.put("type", toolCall.getType());
                    
                    Map<String, Object> functionMap = new HashMap<>();
                    functionMap.put("name", toolCall.getFunction().getName());
                    functionMap.put("arguments", toolCall.getFunction().getArguments());
                    toolCallMap.put("function", functionMap);
                    
                    nativeToolCallData.add(Map.of("tool_call", toolCallMap, "parsing_details", Map.of())); // No parsing details for native
                }
                allToolData.addAll(nativeToolCallData);
            }
            
            // --- Save and Add Assistant Message ---
            Map<String, Object> messageData = new HashMap<>();
            messageData.put("role", "assistant");
            messageData.put("content", content);
            
            // Add native tool calls to the message data if they exist
            if (!nativeToolCallData.isEmpty()) {
                messageData.put("tool_calls", nativeToolCallData.stream()
                    .map(data -> (Map<String, Object>) data.get("tool_call"))
                    .collect(Collectors.toList()));
            }
            
            Message assistantMessage = new Message(threadId, "assistant", messageData, true, Map.of("thread_run_id", threadRunId));
            resultMessages.add(assistantMessage);
            Message savedAssistantMessage = addMessageCallback.apply(threadId, assistantMessage).get();
            
            // --- Execute Tools and Add Results ---
            if (config.isExecuteTools() && !allToolData.isEmpty()) {
                List<Map<String, Object>> toolCallsToExecute = allToolData.stream()
                        .map(data -> (Map<String, Object>) data.get("tool_call"))
                        .collect(Collectors.toList());
                
                List<Map.Entry<Map<String, Object>, ToolResult>> toolResults = 
                        executeTools(toolCallsToExecute, config.getToolExecutionStrategy());
                
                for (int i = 0; i < toolResults.size(); i++) {
                    Map.Entry<Map<String, Object>, ToolResult> entry = toolResults.get(i);
                    Map<String, Object> toolCall = entry.getKey();
                    ToolResult result = entry.getValue();
                    Map<String, Object> originalData = allToolData.get(i);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> parsingDetails = (Map<String, Object>) originalData.get("parsing_details");
                    
                    ToolExecutionContext context = createToolContext(
                        toolCall, i, savedAssistantMessage.getMessageId(), parsingDetails
                    );
                    context.setResult(result);
                    
                    // Save and Add tool_started status
                    Message startedMsg = yieldAndSaveToolStarted(context, threadId, threadRunId);
                    resultMessages.add(startedMsg);
                    
                    // Save tool result
                    Message toolResultMessage = addToolResult(
                        threadId, toolCall, result, config.getXmlAddingStrategy(),
                        savedAssistantMessage.getMessageId(), parsingDetails
                    );
                    
                    // Save and Add completed/failed status
                    Message completedMsg = yieldAndSaveToolCompleted(
                        context, toolResultMessage != null ? toolResultMessage.getMessageId() : null,
                        threadId, threadRunId
                    );
                    
                    resultMessages.add(toolResultMessage);
                    resultMessages.add(completedMsg);
                }
            }
            
            // --- Save and Add Thread Run End Status ---
            Map<String, Object> endContent = Map.of("status_type", "thread_run_end");
            Message endMsg = new Message(threadId, "status", endContent, false, Map.of("thread_run_id", threadRunId));
            resultMessages.add(endMsg);
            addMessageCallback.apply(threadId, endMsg).get();
            
            return CompletableFuture.completedFuture(resultMessages);
            
        } catch (Exception e) {
            logger.error("Error processing non-streaming response: {}", e.getMessage(), e);
            
            // Save and Add error status message
            Map<String, Object> errContent = Map.of(
                "role", "system", "status_type", "error", "message", e.getMessage()
            );
            
            Message errMsg = new Message(threadId, "status", errContent, false, 
                    threadRunId != null ? Map.of("thread_run_id", threadRunId) : null);
            
            try {
                addMessageCallback.apply(threadId, errMsg).get();
                resultMessages.add(errMsg);
            } catch (InterruptedException | ExecutionException ex) {
                logger.error("Error adding error message: {}", ex.getMessage(), ex);
            }
            
            return CompletableFuture.completedFuture(resultMessages);
        }
    }
    
    /**
     * Extract complete XML chunks using start and end pattern matching.
     * 
     * @param content The content to extract XML chunks from
     * @return The extracted XML chunks
     */
    private List<String> extractXmlChunks(String content) {
        List<String> chunks = new ArrayList<>();
        
        // Use a proper XML parser to handle nested tags
        try {
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true); // Important for handling namespaces if they exist
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            
            // Wrap the content in a root element to make it a well-formed XML document
            String wrappedContent = "<root>" + content + "</root>";
            org.w3c.dom.Document doc = builder.parse(new org.xml.sax.InputSource(new java.io.StringReader(wrappedContent)));
            
            org.w3c.dom.NodeList toolCallNodes = doc.getDocumentElement().getChildNodes();
            
            for (int i = 0; i < toolCallNodes.getLength(); i++) {
                org.w3c.dom.Node node = toolCallNodes.item(i);
                if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                    // Serialize the element back to a string to get the XML chunk
                    javax.xml.transform.TransformerFactory tf = javax.xml.transform.TransformerFactory.newInstance();
                    javax.xml.transform.Transformer transformer = tf.newTransformer();
                    transformer.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes");
                    java.io.StringWriter writer = new java.io.StringWriter();
                    transformer.transform(new javax.xml.transform.dom.DOMSource(node), new javax.xml.transform.stream.StreamResult(writer));
                    chunks.add(writer.toString());
                }
            }
            
            return chunks;
        } catch (Exception e) {
            logger.error("Error extracting XML chunks: {}", e.getMessage(), e);
            return List.of(); // Return empty list on error
        }
    }
    
    /**
     * Parse XML chunk into tool call format and return parsing details.
     * 
     * @param xmlChunk The XML chunk to parse
     * @return A map containing the tool call and parsing details, or null if parsing fails
     */
    private Map<String, Object> parseXmlToolCall(String xmlChunk) {
        try {
            // Use DOM parser to parse the XML chunk
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true); // Important for handling namespaces if they exist
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            
            // Parse the XML chunk directly
            org.w3c.dom.Document doc = builder.parse(new org.xml.sax.InputSource(new java.io.StringReader(xmlChunk)));
            
            // Get the root element (which is the tool call tag)
            org.w3c.dom.Element rootElement = doc.getDocumentElement();
            String xmlTagName = rootElement.getTagName();
            
            logger.info("Found XML tag: {}", xmlTagName);
            
            // Get tool info from registry
            Map<String, Object> toolInfo = toolRegistry.getXmlTool(xmlTagName);
            if (toolInfo.isEmpty()) {
                logger.error("No tool found for tag: {}", xmlTagName);
                return null;
            }
            
            
            String functionName = (String) toolInfo.get("method");
            
            // Get the XML schema for this tag
            Object schemaObj = toolInfo.get("schema");
            if (schemaObj == null || !(schemaObj instanceof com.nubian.ai.agentpress.model.ToolSchema)) {
                logger.error("No XML schema found for tag: {}", xmlTagName);
                return null;
            }
            
            com.nubian.ai.agentpress.model.ToolSchema schema = (com.nubian.ai.agentpress.model.ToolSchema) schemaObj;
            com.nubian.ai.agentpress.model.XmlTagSchema xmlSchema = schema.getXmlSchema();
            
            if (xmlSchema == null) {
                logger.error("XML schema is null for tag: {}", xmlTagName);
                return null;
            }
            
            // Parse XML using DOM
            // Wrap the XML chunk in a proper XML document
            String xmlDoc = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + xmlChunk;
            
            // Extract parameters based on XML schema
            Map<String, Object> params = new HashMap<>();
            Map<String, Object> extractedAttributes = new HashMap<>();
            Map<String, Object> extractedElements = new HashMap<>();
            String textContent = null;
            String rootContent = null;
            
            // Process XML mappings - fully implemented to handle all mapping types
            List<com.nubian.ai.agentpress.model.XmlNodeMapping> mappings = xmlSchema.getMappings();
            if (mappings != null) {
                for (com.nubian.ai.agentpress.model.XmlNodeMapping mapping : mappings) {
                    String paramName = mapping.getParamName();
                    String type = mapping.getNodeType();
                    String source = mapping.getPath();
                    String valueType = mapping.getValueType(); // valueType is always present with a default
                    
                    if ("attribute".equals(type)) {
                        // Extract attribute value
                        String attrValue = rootElement.getAttribute(source);
                        if (!attrValue.isEmpty()) {
                            params.put(paramName, convertValue(attrValue, valueType));
                            extractedAttributes.put(source, attrValue);
                        }
                    } else if ("element".equals(type)) {
                        // Extract element value
                        org.w3c.dom.NodeList nodes = rootElement.getElementsByTagName(source);
                        if (nodes.getLength() > 0) {
                            org.w3c.dom.Node node = nodes.item(0);
                            String value = node.getTextContent();
                            params.put(paramName, convertValue(value, valueType));
                            extractedElements.put(source, value);
                        }
                    } else if ("content".equals(type) || "text".equals(type)) {
                        // Extract text content
                        textContent = rootElement.getTextContent().trim();
                        params.put(paramName, convertValue(textContent, valueType));
                    } else if ("root".equals(type)) {
                        // Extract entire XML content
                        rootContent = xmlChunk;
                        params.put(paramName, rootContent);
                    } else if ("xpath".equals(type)) {
                        // Use XPath to extract content
                        try {
                            javax.xml.xpath.XPath xpath = javax.xml.xpath.XPathFactory.newInstance().newXPath();
                            String value = xpath.evaluate(source, rootElement);
                            if (value != null && !value.isEmpty()) {
                                params.put(paramName, convertValue(value, valueType));
                                extractedElements.put(source, value);
                            }
                        } catch (Exception e) {
                            logger.warn("Error evaluating XPath expression '{}': {}", source, e.getMessage());
                        }
                    }
                }
            }
            
            // If no mappings are defined but the tag exists, use text content as the default parameter
            if (params.isEmpty() && textContent == null) {
                textContent = rootElement.getTextContent();
                if (textContent != null && !textContent.isBlank()) {
                    params.put("text", textContent.trim());
                }
            }
            
            // Create tool call with proper arguments
            Map<String, Object> toolCall = new HashMap<>();
            toolCall.put("function_name", functionName);
            toolCall.put("xml_tag_name", xmlTagName);
            toolCall.put("arguments", params);
            
            // Create detailed parsing info
            Map<String, Object> parsingDetails = new HashMap<>();
            parsingDetails.put("attributes", extractedAttributes);
            parsingDetails.put("elements", extractedElements);
            parsingDetails.put("text_content", textContent);
            parsingDetails.put("root_content", rootContent);
            parsingDetails.put("raw_chunk", xmlChunk);
            
            Map<String, Object> result = new HashMap<>();
            result.put("tool_call", toolCall);
            result.put("parsing_details", parsingDetails);
            
            logger.info("Successfully parsed XML tool call: {} with {} parameters", xmlTagName, params.size());
            return result;
        } catch (Exception e) {
            logger.error("Error parsing XML chunk: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Convert a string value to the appropriate Java type.
     * 
     * @param value The string value to convert
     * @param valueType The target type (int, boolean, string, etc.)
     * @return The converted value
     */
    private Object convertValue(String value, String valueType) {
        if (value == null) {
            return null;
        }
        
        try {
            if (valueType == null || valueType.isEmpty() || "string".equals(valueType)) {
                return value;
            } else if ("int".equals(valueType)) {
                return Integer.parseInt(value);
            } else if ("float".equals(valueType) || "double".equals(valueType)) {
                return Double.parseDouble(value);
            } else if ("boolean".equals(valueType)) {
                return Boolean.parseBoolean(value);
            } else if ("json".equals(valueType)) {
                // Parse as JSON object or array
                return objectMapper.readValue(value, Object.class);
            }
        } catch (Exception e) {
            logger.warn("Error converting value '{}' to type {}: {}", value, valueType, e.getMessage());
        }
        
        // Default to string if conversion fails
        return value;
    }
    
    /**
     * Parse XML tool calls from content string.
     * 
     * @param content The content to parse
     * @return A list of maps, each containing a tool call and parsing details
     */
    private List<Map<String, Object>> parseXmlToolCalls(String content) {
        List<Map<String, Object>> parsedData = new ArrayList<>();
        
        try {
            List<String> xmlChunks = extractXmlChunks(content);
            
            for (String xmlChunk : xmlChunks) {
                Map<String, Object> result = parseXmlToolCall(xmlChunk);
                if (result != null) {
                    parsedData.add(result);
                }
            }
        } catch (Exception e) {
            logger.error("Error parsing XML tool calls: {}", e.getMessage(), e);
        }
        
        return parsedData;
    }
    
    /**
     * Execute a single tool call asynchronously.
     * 
     * @param toolCall The tool call to execute
     * @return A CompletableFuture that completes with the tool result
     */
    private CompletableFuture<ToolResult> executeToolAsync(Map<String, Object> toolCall) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeTool(toolCall);
            } catch (Exception e) {
                logger.error("Error executing tool: {}", e.getMessage(), e);
                return new ToolResult(false, "Error executing tool: " + e.getMessage());
            }
        });
    }
    
    /**
     * Execute a single tool call.
     * 
     * @param toolCall The tool call to execute
     * @return The tool result
     */
    private ToolResult executeTool(Map<String, Object> toolCall) {
        try {
            String functionName = (String) toolCall.get("function_name");
            @SuppressWarnings("unchecked")
            Map<String, Object> arguments = (Map<String, Object>) toolCall.get("arguments");
            
            logger.info("Executing tool: {} with arguments: {}", functionName, arguments);
            
            // Get available functions from tool registry
            Map<String, java.util.function.Function<Map<String, Object>, Object>> availableFunctions = 
                    toolRegistry.getAvailableFunctions();
            
            // Look up the function by name
            java.util.function.Function<Map<String, Object>, Object> toolFn = availableFunctions.get(functionName);
            if (toolFn == null) {
                logger.error("Tool function '{}' not found in registry", functionName);
                return new ToolResult(false, "Tool function '" + functionName + "' not found");
            }
            
            logger.debug("Found tool function for '{}', executing...", functionName);
            Object result = toolFn.apply(arguments);
            
            if (result instanceof ToolResult) {
                return (ToolResult) result;
            } else {
                logger.info("Tool execution complete: {} -> {}", functionName, result);
                return new ToolResult(true, result != null ? result.toString() : "null");
            }
        } catch (Exception e) {
            logger.error("Error executing tool {}: {}", toolCall.get("function_name"), e.getMessage(), e);
            return new ToolResult(false, "Error executing tool: " + e.getMessage());
        }
    }
    
    /**
     * Execute tool calls with the specified strategy.
     *
     * @param toolCalls The tool calls to execute
     * @param executionStrategy The execution strategy
     * @return A list of entries containing the original tool call and its result
     */
    private List<Map.Entry<Map<String, Object>, ToolResult>> executeTools(
            List<Map<String, Object>> toolCalls,
            String executionStrategy) {
        
        logger.info("Executing {} tools with strategy: {}", toolCalls.size(), executionStrategy);
        
        if ("parallel".equals(executionStrategy)) {
            return executeToolsInParallel(toolCalls);
        } else {
            return executeToolsSequentially(toolCalls);
        }
    }
    
    /**
     * Execute tool calls sequentially.
     * 
     * @param toolCalls The tool calls to execute
     * @return A list of entries containing the original tool call and its result
     */
    private List<Map.Entry<Map<String, Object>, ToolResult>> executeToolsSequentially(
            List<Map<String, Object>> toolCalls) {
        
        List<Map.Entry<Map<String, Object>, ToolResult>> results = new ArrayList<>();
        
        for (Map<String, Object> toolCall : toolCalls) {
            ToolResult result = executeTool(toolCall);
            results.add(Map.entry(toolCall, result));
        }
        
        return results;
    }
    
    /**
     * Execute tool calls in parallel.
     *
     * @param toolCalls The tool calls to execute
     * @return A list of entries containing the original tool call and its result
     */
    private List<Map.Entry<Map<String, Object>, ToolResult>> executeToolsInParallel(
            List<Map<String, Object>> toolCalls) {
        
        List<CompletableFuture<Map.Entry<Map<String, Object>, ToolResult>>> futures = 
                toolCalls.stream()
                        .map(toolCall -> executeToolAsync(toolCall)
                                .thenApply(result -> Map.entry(toolCall, result)))
                        .collect(Collectors.toList());
        
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));
        
        try {
            allFutures.get();
            
                    return futures.stream()
                            .map(future -> {
                                try {
                                    return future.get();
                                } catch (InterruptedException | ExecutionException e) {
                                    logger.error("Error getting tool result: {}", e.getMessage(), e);
                                    // Create a simple map with empty tool call and error result
                                    Map<String, Object> emptyToolCall = Map.of();
                                    ToolResult errorResult = new ToolResult(false, "Error: " + e.getMessage());
                                    return Map.entry(emptyToolCall, errorResult);
                                }
                            })
                            .collect(Collectors.toList());
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error executing tools in parallel: {}", e.getMessage(), e);
            return List.of();
        }
    }
    
    /**
     * Add a tool result to the conversation thread.
     * 
     * @param threadId The ID of the thread
     * @param toolCall The tool call
     * @param result The tool result
     * @param strategy The strategy for adding XML tool results
     * @param assistantMessageId The ID of the assistant message that generated the tool call
     * @param parsingDetails Detailed parsing info for XML calls
     * @return The saved message
     */
    private Message addToolResult(
            String threadId,
            Map<String, Object> toolCall,
            ToolResult result,
            String strategy,
            String assistantMessageId,
            Map<String, Object> parsingDetails) {
        
        try {
            // Create metadata with assistant_message_id if provided
            Map<String, Object> metadata = new HashMap<>();
            if (assistantMessageId != null) {
                metadata.put("assistant_message_id", assistantMessageId);
                logger.info("Linking tool result to assistant message: {}", assistantMessageId);
            }
            
            // Add parsing details to metadata if available
            if (parsingDetails != null) {
                metadata.put("parsing_details", parsingDetails);
                logger.info("Adding parsing_details to tool result metadata");
            }
            
            // Check if this is a native function call (has id field)
            if (toolCall.containsKey("id")) {
                // Format as a proper tool message according to OpenAI spec
                String functionName = (String) toolCall.get("function_name");
                
                // Format the tool result content
                String content;
                if (result.getOutput() instanceof String) {
                    content = (String) result.getOutput();
                } else if (result.getOutput() != null) {
                    try {
                        content = objectMapper.writeValueAsString(result.getOutput());
                    } catch (JsonProcessingException e) {
                        content = result.getOutput().toString();
                    }
                } else {
                    content = "null";
                }
                
                logger.info("Formatted tool result content: {}", content.length() > 100 ? 
                        content.substring(0, 100) + "..." : content);
                
                // Create the tool response message with proper format
                Map<String, Object> toolMessage = Map.of(
                    "role", "tool",
                    "tool_call_id", toolCall.get("id"),
                    "name", functionName,
                    "content", content
                );
                
                logger.info("Adding native tool result for tool_call_id={} with role=tool", toolCall.get("id"));
                
                // Add as a tool message to the conversation history
                Message message = new Message(threadId, "tool", toolMessage, true, metadata);
                return addMessageCallback.apply(threadId, message).get();
            }
            
            // For XML and other non-native tools
            String resultRole = "user_message".equals(strategy) ? "user" : "assistant";
            
            // Create a context for consistent formatting
            ToolExecutionContext context = createToolContext(toolCall, 0, assistantMessageId, parsingDetails);
            context.setResult(result);
            
            // Format the content
            String content = formatXmlToolResult(toolCall, result);
            
            // Add the message with the appropriate role to the conversation history
            Map<String, Object> resultMessage = Map.of(
                "role", resultRole,
                "content", content
            );
            
            Message message = new Message(threadId, "tool", resultMessage, true, metadata);
            return addMessageCallback.apply(threadId, message).get();
            
        } catch (Exception e) {
            logger.error("Error adding tool result: {}", e.getMessage(), e);
            
            // Fallback to a simple message
            try {
                Map<String, Object> fallbackMessage = Map.of(
                    "role", "user",
                    "content", result.toString()
                );
                
                Map<String, Object> metadata = new HashMap<>();
                if (assistantMessageId != null) {
                    metadata.put("assistant_message_id", assistantMessageId);
                }
                
                Message message = new Message(threadId, "tool", fallbackMessage, true, metadata);
                return addMessageCallback.apply(threadId, message).get();
            } catch (Exception e2) {
                logger.error("Failed even with fallback message: {}", e2.getMessage(), e2);
                return null;
            }
        }
    }
    
    /**
     * Format a tool result wrapped in a <tool_result> tag.
     * 
     * @param toolCall The tool call that was executed
     * @param result The result of the tool execution
     * @return String containing the formatted result
     */
    private String formatXmlToolResult(Map<String, Object> toolCall, ToolResult result) {
        // Always use xml_tag_name if it exists
        if (toolCall.containsKey("xml_tag_name")) {
            String xmlTagName = (String) toolCall.get("xml_tag_name");
            return String.format("<tool_result> <%s> %s </%s> </tool_result>", 
                    xmlTagName, result.toString(), xmlTagName);
        }
        
        // Non-XML tool, just return the function result
        String functionName = (String) toolCall.get("function_name");
        return String.format("Result for %s: %s", functionName, result.toString());
    }
    
    /**
     * Create a tool execution context with display name and parsing details populated.
     * 
     * @param toolCall The tool call
     * @param toolIndex The index of the tool in the sequence
     * @param assistantMessageId The ID of the assistant message that generated the tool call
     * @param parsingDetails Detailed parsing info for XML calls
     * @return The tool execution context
     */
    private ToolExecutionContext createToolContext(
            Map<String, Object> toolCall, 
            int toolIndex, 
            String assistantMessageId, 
            Map<String, Object> parsingDetails) {
        
        ToolExecutionContext context = new ToolExecutionContext(toolCall, toolIndex);
        context.setAssistantMessageId(assistantMessageId);
        context.setParsingDetails(parsingDetails);
        
        // Set function_name and xml_tag_name fields
        if (toolCall.containsKey("xml_tag_name")) {
            context.setXmlTagName((String) toolCall.get("xml_tag_name"));
            context.setFunctionName((String) toolCall.get("function_name"));
        } else {
            // For non-XML tools, use function name directly
            context.setFunctionName((String) toolCall.getOrDefault("function_name", "unknown"));
        }
        
        return context;
    }
    
    /**
     * Format, save, and return a tool started status message.
     * 
     * @param context The tool execution context
     * @param threadId The ID of the thread
     * @param threadRunId The ID of the thread run
     * @return The saved message
     */
    private Message yieldAndSaveToolStarted(
            ToolExecutionContext context, 
            String threadId, 
            String threadRunId) {
        
        String toolName = context.getXmlTagName() != null ? context.getXmlTagName() : context.getFunctionName();
        
        Map<String, Object> content = new HashMap<>();
        content.put("role", "assistant");
        content.put("status_type", "tool_started");
        content.put("function_name", context.getFunctionName());
        content.put("xml_tag_name", context.getXmlTagName());
        content.put("message", "Starting execution of " + toolName);
        content.put("tool_index", context.getToolIndex());
        
        if (context.getToolCall().containsKey("id")) {
            content.put("tool_call_id", context.getToolCall().get("id"));
        }
        
        Map<String, Object> metadata = Map.of("thread_run_id", threadRunId);
        
        try {
            Message message = new Message(threadId, "status", content, false, metadata);
            return addMessageCallback.apply(threadId, message).get();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error saving tool started status: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Format, save, and return a tool completed/failed status message.
     * 
     * @param context The tool execution context
     * @param toolMessageId The ID of the tool result message
     * @param threadId The ID of the thread
     * @param threadRunId The ID of the thread run
     * @return The saved message
     */
    private Message yieldAndSaveToolCompleted(
            ToolExecutionContext context, 
            String toolMessageId, 
            String threadId, 
            String threadRunId) {
        
        if (context.getResult() == null) {
            return yieldAndSaveToolError(context, threadId, threadRunId);
        }
        
        String toolName = context.getXmlTagName() != null ? context.getXmlTagName() : context.getFunctionName();
        String statusType = context.getResult().isSuccess() ? "tool_completed" : "tool_failed";
        String messageText = "Tool " + toolName + " " + 
                (context.getResult().isSuccess() ? "completed successfully" : "failed");
        
        Map<String, Object> content = new HashMap<>();
        content.put("role", "assistant");
        content.put("status_type", statusType);
        content.put("function_name", context.getFunctionName());
        content.put("xml_tag_name", context.getXmlTagName());
        content.put("message", messageText);
        content.put("tool_index", context.getToolIndex());
        
        if (context.getToolCall().containsKey("id")) {
            content.put("tool_call_id", context.getToolCall().get("id"));
        }
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("thread_run_id", threadRunId);
        
        // Add the actual tool result message ID to the metadata if available and successful
        if (context.getResult().isSuccess() && toolMessageId != null) {
            metadata.put("linked_tool_result_message_id", toolMessageId);
        }
        
        // Signal if this is a terminating tool
        if ("ask".equals(context.getFunctionName()) || "complete".equals(context.getFunctionName())) {
            metadata.put("agent_should_terminate", true);
            logger.info("Marking tool status for '{}' with termination signal.", context.getFunctionName());
        }
        
        try {
            Message message = new Message(threadId, "status", content, false, metadata);
            return addMessageCallback.apply(threadId, message).get();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error saving tool completed status: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Format, save, and return a tool error status message.
     * 
     * @param context The tool execution context
     * @param threadId The ID of the thread
     * @param threadRunId The ID of the thread run
     * @return The saved message
     */
    private Message yieldAndSaveToolError(
            ToolExecutionContext context, 
            String threadId, 
            String threadRunId) {
        
        String errorMsg = context.getError() != null ? context.getError().getMessage() : "Unknown error during tool execution";
        String toolName = context.getXmlTagName() != null ? context.getXmlTagName() : context.getFunctionName();
        
        Map<String, Object> content = new HashMap<>();
        content.put("role", "assistant");
        content.put("status_type", "tool_error");
        content.put("function_name", context.getFunctionName());
        content.put("xml_tag_name", context.getXmlTagName());
        content.put("message", "Error executing tool " + toolName + ": " + errorMsg);
        content.put("tool_index", context.getToolIndex());
        
        if (context.getToolCall().containsKey("id")) {
            content.put("tool_call_id", context.getToolCall().get("id"));
        }
        
        Map<String, Object> metadata = Map.of("thread_run_id", threadRunId);
        
        try {
            Message message = new Message(threadId, "status", content, false, metadata);
            return addMessageCallback.apply(threadId, message).get();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error saving tool error status: {}", e.getMessage(), e);
            return null;
        }
    }
}
