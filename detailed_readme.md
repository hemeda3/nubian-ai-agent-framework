# Nubian AI Java: Detailed Technical Documentation

This document provides in-depth technical details about the Nubian AI Java implementation, diving into internal components, data flows, and system architecture.

## Table of Contents

1. [System Architecture](#system-architecture)
2. [Database Schema](#database-schema)
3. [Thread and Message Management](#thread-and-message-management)
4. [LLM Integration](#llm-integration)
5. [Tool Execution System](#tool-execution-system)
6. [Caching Strategy](#caching-strategy)
7. [Authentication and Authorization](#authentication-and-authorization)
8. [Background Processing](#background-processing)
9. [Error Handling and Recovery](#error-handling-and-recovery)
10. [Performance Considerations](#performance-considerations)
11. [Monitoring and Observability](#monitoring-and-observability)
12. [Extension Points](#extension-points)

## System Architecture

### Core Components Relationships

The system is built around several key components that interact in a layered architecture:

```
┌───────────────────────────────────────────────────────────────────┐
│                            Client Layer                           │
└─────────────────────────────────┬─────────────────────────────────┘
                                  │
                                  ▼
┌───────────────────────────────────────────────────────────────────┐
│                         Controller Layer                          │
│  ┌─────────────────┐  ┌────────────────┐  ┌────────────────────┐  │
│  │ AgentController │  │ AuthController │  │ BillingController  │  │
│  └─────────────────┘  └────────────────┘  └────────────────────┘  │
└─────────────────────────────┬─────────────────────────────────────┘
                              │
                              ▼
┌───────────────────────────────────────────────────────────────────┐
│                        Service Layer (Agent)                      │
│  ┌─────────────────┐  ┌──────────────────┐  ┌──────────────────┐  │
│  │ AgentRunManager │  │ BackgroundService │  │ ToolOrchestrator │  │
│  └─────────────────┘  └──────────────────┘  └──────────────────┘  │
└─────────────────────────────┬─────────────────────────────────────┘
                              │
                              ▼
┌───────────────────────────────────────────────────────────────────┐
│                    Service Layer (AgentPress)                     │
│  ┌────────────────┐  ┌───────────────┐  ┌─────────────────────┐   │
│  │ ThreadManager  │  │ ToolRegistry  │  │ ResponseProcessor   │   │
│  └────────────────┘  └───────────────┘  └─────────────────────┘   │
│  ┌────────────────┐  ┌───────────────┐  ┌─────────────────────┐   │
│  │ ContextManager │  │ LlmService    │  │ StorageService      │   │
│  └────────────────┘  └───────────────┘  └─────────────────────┘   │
└─────────────────────────────┬─────────────────────────────────────┘
                              │
                              ▼
┌───────────────────────────────────────────────────────────────────┐
│                      Infrastructure Layer                         │
│  ┌────────────────┐  ┌───────────────┐  ┌─────────────────────┐   │
│  │ DBConnection   │  │ RedisService  │  │ RabbitMQ Client     │   │
│  └────────────────┘  └───────────────┘  └─────────────────────┘   │
└───────────────────────────────────────────────────────────────────┘
```

### Package Structure in Detail

- **com.nubian.ai**
  - **agentpress**: Core framework components
    - **agent**: Framework-defined agent interfaces and base classes
    - **annotations**: Custom annotations for tools and other components
    - **config**: Framework configuration and settings
    - **exception**: Framework-specific exceptions
    - **model**: Data models (Tool, Message, etc.)
    - **sandbox**: Sandbox environment for safe tool execution
    - **service**: Core services (ThreadManager, ContextManager, etc.)
    - **tool**: Built-in tool implementations
    - **util**: Utility classes and helpers
  - **agent**: Nubian agent implementation
    - **config**: Agent-specific configuration
    - **controller**: API endpoints for agent interaction
    - **model**: Agent-specific models (AgentRunRequest, etc.)
    - **service**: Agent services (AgentRunManager, etc.)
    - **tool**: Agent-specific tools
    - **tool.providers**: Data providers for tools

### Dependency Injection and Component Management

The application uses Spring's dependency injection system:

1. **Component Scanning**: Automatically detects components in the `com.nubian.ai` package hierarchy
2. **Configuration Classes**: Define beans and their relationships
3. **Conditional Bean Creation**: Uses Spring profiles to create different beans based on environment

Example of configuration class:

```java
@Configuration
public class AgentPressConfig {
    @Bean
    @ConditionalOnMissingBean
    public ThreadManager threadManager(DBConnection dbConnection, RedisService redisService) {
        return new ThreadManager(dbConnection, redisService);
    }

    @Bean
    @ConditionalOnMissingBean
    public ToolRegistry toolRegistry() {
        return new ToolRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public ResponseProcessor responseProcessor(ToolRegistry toolRegistry) {
        return new ResponseProcessor(toolRegistry);
    }
    
    // Other beans...
}
```

## Database Schema

The system uses a PostgreSQL database with the following key tables:

### Agent Run Tracking

```sql
CREATE TABLE public.agent_runs (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    thread_id VARCHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL,
    request JSONB NOT NULL,
    result JSONB,
    error TEXT,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE,
    model VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX agent_runs_user_id_idx ON public.agent_runs(user_id);
CREATE INDEX agent_runs_thread_id_idx ON public.agent_runs(thread_id);
CREATE INDEX agent_runs_status_idx ON public.agent_runs(status);
```

### Threads and Messages

```sql
CREATE TABLE public.threads (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    title TEXT,
    summary TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE public.messages (
    id VARCHAR(36) PRIMARY KEY,
    thread_id VARCHAR(36) NOT NULL REFERENCES public.threads(id),
    type VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    llm_message BOOLEAN NOT NULL DEFAULT FALSE,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    FOREIGN KEY (thread_id) REFERENCES public.threads(id)
);

CREATE INDEX messages_thread_id_idx ON public.messages(thread_id);
CREATE INDEX messages_type_idx ON public.messages(type);
```

### User Management

```sql
CREATE TABLE basejump.users (
    id UUID PRIMARY KEY,
    email TEXT UNIQUE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE basejump.accounts (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES basejump.users(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT accounts_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES basejump.users(id)
);

CREATE TABLE basejump.account_user (
    account_id UUID NOT NULL REFERENCES basejump.accounts(id),
    user_id UUID NOT NULL REFERENCES basejump.users(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    PRIMARY KEY (account_id, user_id),
    CONSTRAINT account_user_account_id_fkey FOREIGN KEY (account_id) REFERENCES basejump.accounts(id),
    CONSTRAINT account_user_user_id_fkey FOREIGN KEY (user_id) REFERENCES basejump.users(id)
);
```

### Billing and Subscription

```sql
CREATE TABLE basejump.billing_customers (
    id VARCHAR(255) PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES basejump.accounts(id),
    active BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT billing_customers_account_id_fkey FOREIGN KEY (account_id) REFERENCES basejump.accounts(id)
);

CREATE TABLE basejump.billing_subscriptions (
    id VARCHAR(255) PRIMARY KEY,
    customer_id VARCHAR(255) NOT NULL REFERENCES basejump.billing_customers(id),
    price_id VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    period_start TIMESTAMP WITH TIME ZONE,
    period_end TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT billing_subscriptions_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES basejump.billing_customers(id)
);
```

### Database Access Layer

The `DBConnection` class provides a thin wrapper over Spring's `JdbcTemplate` with methods optimized for:

1. **Asynchronous Operations**: Uses `CompletableFuture` for non-blocking database access
2. **Prepared Statements**: Prevents SQL injection via parameterized queries
3. **Connection Pooling**: Uses HikariCP for efficient connection management
4. **Transaction Management**: Supports atomic operations across multiple queries

Example of asynchronous message insertion:

```java
public CompletableFuture<Message> insertMessage(Message message) {
    return CompletableFuture.supplyAsync(() -> {
        try {
            String sql = "INSERT INTO messages (id, thread_id, type, content, llm_message, metadata) " +
                         "VALUES (?, ?, ?, ?, ?, ?)";
            
            String contentStr = message.getContent() instanceof String ? 
                (String) message.getContent() : 
                objectMapper.writeValueAsString(message.getContent());
                
            String metadataJson = message.getMetadata() != null ? 
                objectMapper.writeValueAsString(message.getMetadata()) : 
                "{}";
                
            jdbcTemplate.update(
                sql,
                message.getMessageId(),
                message.getThreadId(),
                message.getType(),
                contentStr,
                message.isLlmMessage(),
                metadataJson
            );
            
            return message;
        } catch (Exception e) {
            logger.error("Failed to insert message: {}", e.getMessage());
            throw new RuntimeException("Failed to insert message", e);
        }
    }, executor);
}
```

## Thread and Message Management

### Thread Lifecycle

1. **Creation**: Threads are created via `ThreadManager.createThread()`
2. **Message Addition**: Messages are added via `ThreadManager.addMessage()`
3. **LLM Interaction**: The LLM is invoked via `ThreadManager.runLlm()`
4. **Context Management**: Token limits are enforced via `ContextManager`
5. **Persistence**: Messages are stored in the database and cached in Redis

### Thread Manager Implementation

The `ThreadManager` coordinates thread operations:

```java
public class ThreadManager {
    private final DBConnection dbConnection;
    private final RedisService redisService;
    private final ContextManager contextManager;
    
    public String createThread() {
        String threadId = UUID.randomUUID().toString();
        // Persist thread to database
        // Initialize thread in Redis
        return threadId;
    }
    
    public void addMessage(String threadId, Message message) {
        // Validate message
        // Update context via ContextManager
        // Persist message to database
        // Update thread in Redis
    }
    
    public LlmResponse runLlm(String threadId, List<Tool> tools, String modelName) {
        // Retrieve thread messages
        // Apply context management (summarization if needed)
        // Prepare LLM request
        // Execute LLM call
        // Process response
        // Add response to thread
        return llmResponse;
    }
}
```

### Message Flow and Processing

Messages go through several stages:

1. **Initial Creation**: Created by user input or system
2. **Preprocessing**: Metadata added, tokens counted
3. **Storage**: Persisted to database and Redis
4. **Context Selection**: ContextManager determines which messages to include
5. **LLM Input**: Selected messages sent to LLM
6. **Response Processing**: LLM response parsed for tool calls
7. **Tool Execution**: Any tool calls are executed
8. **Result Storage**: Tool results and final response stored

## LLM Integration

### LLM Service Interface

The system abstracts LLM providers through a common interface:

```java
public interface LlmService {
    /**
     * Generate content using the specified model and messages
     */
    LlmResponse generateContent(List<Message> messages, List<Tool> tools);
    
    /**
     * Generate content with the provided configuration
     */
    LlmResponse generateContent(List<Message> messages, List<Tool> tools, ProcessorConfig config);
    
    /**
     * Get token usage for a list of messages
     */
    int getTokenUsage(List<Message> messages);
}
```

### Provider Implementations

Multiple LLM providers are supported:

1. **OpenAI Implementation**:

```java
@Service
public class OpenAILlmService implements LlmService {
    
    private final OpenAIClient client;
    
    @Override
    public LlmResponse generateContent(List<Message> messages, List<Tool> tools) {
        // Convert messages to OpenAI format
        // Convert tools to OpenAI tool format
        // Create API request
        // Execute request
        // Parse response
        // Extract tool calls if any
        return response;
    }
}
```

2. **Google Gemini Implementation**:

```java
@Service
public class DefaultLlmService implements LlmService {
    
    private final GenerativeModel model;
    
    @Override
    public LlmResponse generateContent(List<Message> messages, List<Tool> tools) {
        // Convert messages to Gemini format
        // Convert tools to function declarations
        // Create prompt
        // Execute request
        // Parse response
        // Extract tool calls if any
        return response;
    }
}
```

### Response Processing

The `ResponseProcessor` class handles parsing LLM responses:

```java
public class ResponseProcessor {
    
    private final ToolRegistry toolRegistry;
    
    public ToolExecutionContext processResponse(LlmResponse response) {
        // Extract assistant message
        // Parse message for tool calls
        // Validate tool calls against registry
        // Prepare execution context
        return toolExecutionContext;
    }
    
    public void executeTools(ToolExecutionContext context) {
        // For each tool call
        // Find tool in registry
        // Execute tool with parameters
        // Collect results
        // Update context with results
    }
}
```

## Tool Execution System

### Tool Registration

Tools are registered via the `ToolRegistry`:

```java
public class ToolRegistry {
    private final Map<String, Tool> tools = new ConcurrentHashMap<>();
    
    public void registerTool(Tool tool) {
        // Validate tool
        // Add to registry
        tools.put(tool.getName(), tool);
    }
    
    public List<Tool> getTools() {
        return new ArrayList<>(tools.values());
    }
    
    public Tool getTool(String name) {
        return tools.get(name);
    }
}
```

### Tool Definition

Tools are defined using a standardized interface:

```java
public class Tool {
    private String name;
    private String description;
    private ToolSchema schema;
    
    // Tool's execution logic
    public ToolResult execute(Map<String, Object> parameters) {
        // Validate parameters against schema
        // Execute tool logic
        // Return result
        return new ToolResult(true, result, null);
    }
    
    // Tool's schema definition
    public ToolSchema getSchema() {
        return schema;
    }
}
```

### Schema Validation

Parameters are validated against the tool's schema:

```java
public class ToolSchema {
    private String type;
    private Map<String, CustomSchema> properties;
    private List<String> required;
    
    public boolean validate(Map<String, Object> parameters) {
        // Check required fields
        // Validate types
        // Validate constraints
        return valid;
    }
}
```

### Tool Discovery via Annotations

Tools can be automatically discovered using annotations:

```java
@Component
public class DataProviderTool {

    @ToolFunction(
        name = "get_data",
        description = "Retrieves data from a specified provider"
    )
    public Map<String, Object> getData(
        @Parameter(name = "provider", description = "The data provider to use") String provider,
        @Parameter(name = "query", description = "The search query") String query
    ) {
        // Tool implementation
        return result;
    }
}
```

## Caching Strategy

### Redis Cache Structure

The system uses Redis for several caching purposes:

1. **Thread Cache**:
   - Key: `thread:{threadId}`
   - Value: Thread metadata as JSON
   - TTL: 24 hours

2. **Message Cache**:
   - Key: `thread:{threadId}:messages`
   - Value: List of message IDs
   - TTL: 24 hours

3. **Message Content Cache**:
   - Key: `message:{messageId}`
   - Value: Message content as JSON
   - TTL: 24 hours

4. **Run Status Cache**:
   - Key: `run:{runId}:status`
   - Value: Current run status
   - TTL: 1 hour

5. **User Session Cache**:
   - Key: `session:{sessionId}`
   - Value: User session data
   - TTL: Session timeout (configurable)

### Redis Service Implementation

```java
@Service
public class RedisService {
    
    private final StringRedisTemplate redisTemplate;
    
    // Thread operations
    public void cacheThread(String threadId, Map<String, Object> threadData) {
        String key = "thread:" + threadId;
        redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(threadData));
        redisTemplate.expire(key, Duration.ofHours(24));
    }
    
    public Map<String, Object> getThreadData(String threadId) {
        String key = "thread:" + threadId;
        String data = redisTemplate.opsForValue().get(key);
        return data != null ? objectMapper.readValue(data, MAP_TYPE_REF) : null;
    }
    
    // Message operations
    public void addMessage(String threadId, String messageId, Message message) {
        String threadKey = "thread:" + threadId + ":messages";
        String messageKey = "message:" + messageId;
        
        redisTemplate.opsForList().rightPush(threadKey, messageId);
        redisTemplate.opsForValue().set(messageKey, objectMapper.writeValueAsString(message));
        
        redisTemplate.expire(threadKey, Duration.ofHours(24));
        redisTemplate.expire(messageKey, Duration.ofHours(24));
    }
    
    public List<Message> getThreadMessages(String threadId) {
        String key = "thread:" + threadId + ":messages";
        List<String> messageIds = redisTemplate.opsForList().range(key, 0, -1);
        
        if (messageIds == null || messageIds.isEmpty()) {
            return Collections.emptyList();
        }
        
        return messageIds.stream()
            .map(id -> redisTemplate.opsForValue().get("message:" + id))
            .filter(Objects::nonNull)
            .map(this::deserializeMessage)
            .collect(Collectors.toList());
    }
    
    // Run status operations
    public void setRunStatus(String runId, String status) {
        String key = "run:" + runId + ":status";
        redisTemplate.opsForValue().set(key, status);
        redisTemplate.expire(key, Duration.ofHours(1));
    }
    
    // Pub/Sub operations
    public void publish(String channel, String message) {
        redisTemplate.convertAndSend(channel, message);
    }
    
    public void subscribe(String channel, MessageListener listener) {
        redisTemplate.getConnectionFactory().getConnection().subscribe(
            listener, channel.getBytes(StandardCharsets.UTF_8)
        );
    }
}
```

### Caching Strategy Decisions

1. **Thread and Message Caching**: Most recent threads and messages are cached for fast access, reducing database load during active conversations
2. **Run Status Caching**: Agent run status is cached to support frequent polling by clients
3. **TTL Management**: Different cache entries have different TTLs based on access patterns
4. **Cache Invalidation**: Explicit invalidation when entities are updated or deleted
5. **Cache Warming**: Proactive caching of frequently accessed data

## Authentication and Authorization

### Authentication Flow

1. **JWT Verification**:
   - Token extracted from Authorization header
   - Token validity checked
   - User ID extracted from claims

```java
public class JwtUtils {
    
    public static String getUserIdFromToken(String token) {
        try {
            // Use a dummy key since we're only interested in parsing the token
            Key dummyKey = Keys.hmacShaKeyFor(new byte[32]);
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(dummyKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
            
            String userId = claims.get("sub", String.class);
            if (userId == null || userId.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token payload");
            }
            
            return userId;
        } catch (JwtException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token: " + e.getMessage());
        }
    }
}
```

2. **User Retrieval**:
   - User ID used to query database
   - User details and permissions loaded

```java
@Service
public class UserService {
    
    private final DBConnection dbConnection;
    
    public User getUserById(String userId) {
        String sql = "SELECT * FROM basejump.users WHERE id = ?";
        List<Map<String, Object>> results = dbConnection.queryForList(sql, userId);
        
        if (results.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        
        return mapUserFromRow(results.get(0));
    }
    
    public List<String> getUserPermissions(String userId) {
        // Query for user roles and permissions
        return permissions;
    }
}
```

3. **Authorization Enforcement**:
   - Controllers check permissions before processing
   - Method-level security annotations
   - Custom security expressions

```java
@RestController
@RequestMapping("/api/agent")
public class AgentController {
    
    @PostMapping("/runs")
    public ResponseEntity<AgentRunResponse> createRun(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody AgentRunRequest request) {
        
        // Extract user ID from JWT
        String userId = JwtUtils.getUserIdFromToken(getTokenFromHeader(authHeader));
        
        // Check if user has permission to create runs
        if (!userService.hasPermission(userId, "agent:runs:create")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient permissions");
        }
        
        // Process the request
        AgentRunResponse response = agentRunManager.createRun(userId, request);
        return ResponseEntity.ok(response);
    }
    
    private String getTokenFromHeader(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authorization header");
    }
}
```

### Security Considerations

1. **JWT Handling**:
   - Token expiration
   - Signature verification
   - Claim validation

2. **SQL Injection Prevention**:
   - Parameterized queries
   - Input validation
   - Prepared statements

3. **XSS Prevention**:
   - Input sanitization
   - Content Security Policy
   - Output encoding

4. **CSRF Protection**:
   - CSRF tokens
   - SameSite cookies
   - Origin validation

## Background Processing

### RabbitMQ Integration

The system uses RabbitMQ for reliable task distribution:

```java
@Configuration
public class RabbitConfig {
    
    @Bean
    public Queue agentRunQueue() {
        return new Queue("agent-runs", true);
    }
    
    @Bean
    public DirectExchange agentExchange() {
        return new DirectExchange("agent-exchange");
    }
    
    @Bean
    public Binding agentRunBinding(Queue agentRunQueue, DirectExchange agentExchange) {
        return BindingBuilder.bind(agentRunQueue).to(agentExchange).with("agent.run");
    }
    
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(new Jackson2JsonMessageConverter());
        return template;
    }
}
```

### Background Service Implementation

The `AgentBackgroundService` processes agent runs asynchronously:

```java
@Service
public class AgentBackgroundService {
    
    private final AgentRunManager agentRunManager;
    private final RedisService redisService;
    private final DBConnection dbConnection;
    
    @RabbitListener(queues = "agent-runs")
    public void handleAgentRun(AgentRunRequest request) {
        String runId = request.getRunId();
        
        try {
            // Update status to processing
            agentRunManager.setAgentRunStatus(runId, "processing", null);
            
            // Process the run
            AgentRunResponse response = processRun(runId, request);
            
            // Update with success status
            agentRunManager.setAgentRunStatus(runId, "completed", null);
            
            // Store result
            storeResult(runId, response);
            
        } catch (Exception e) {
            // Handle failure
            logger.error("Error processing agent run {}: {}", runId, e.getMessage(), e);
            agentRunManager.setAgentRunStatus(runId, "failed", e.getMessage());
        }
    }
    
    private AgentRunResponse processRun(String runId, AgentRunRequest request) {
        // Create thread if needed
        String threadId = request.getThreadId() != null ? 
            request.getThreadId() : threadManager.createThread();
        
        // Add user message if provided
        if (request.getMessage() != null) {
            Message userMessage = new Message();
            userMessage.setThreadId(threadId);
            userMessage.setType("user");
            userMessage.setContent(request.getMessage());
            threadManager.addMessage(threadId, userMessage);
        }
        
        // Get appropriate tools
        List<Tool> tools = toolRegistry.getTools();
        
        // Run LLM
        LlmResponse llmResponse = threadManager.runLlm(threadId, tools, request.getModel());
        
        // Process response for tool calls
        ToolExecutionContext context = responseProcessor.processResponse(llmResponse);
        
        // Execute tools if any
        if (!context.getToolCalls().isEmpty()) {
            responseProcessor.executeTools(context);
            
            // Run LLM again with tool results if needed
            if (context.hasResults()) {
                llmResponse = threadManager.runLlm(threadId, tools, request.getModel());
            }
        }
        
        // Prepare response
        AgentRunResponse response = new AgentRunResponse();
        response.setRunId(runId);
        response.setThreadId(threadId);
        response.setStatus("completed");
        response.setResult(llmResponse.getContent());
        
        return response;
    }
    
    private void storeResult(String runId, AgentRunResponse response) {
        String sql = "UPDATE agent_runs SET result = ?, status = ?, end_time = NOW() WHERE id = ?";
        String resultJson = objectMapper.writeValueAsString(response.getResult());
        dbConnection.update(sql, resultJson, "completed", runId);
    }
}
```

### Status Tracking and Event Publishing

Run status is tracked and published via Redis:

```java
public void setAgentRunStatus(String runId, String status, String errorMessage) {
    // Update in database
    String sql = "UPDATE agent_runs SET status = ?, error = ?, updated_at = NOW() " +
                 "WHERE id = ?";
    dbConnection.update(sql, status, errorMessage, runId);
    
    // Update in Redis
    redisService.setRunStatus(runId, status);
    
    // Publish event
    Map<String, Object> event = new HashMap<>();
    event.put("runId", runId);
    event.put("status", status);
    if (errorMessage != null) {
        event.put("error", errorMessage);
    }
    
    String eventJson = objectMapper.writeValueAsString(event);
    redisService.publish("agent-run-events", eventJson);
}
```

## Error Handling and Recovery

### Exception Hierarchy

The system uses a structured exception hierarchy:

```java
// Base exception for all application exceptions
public class AgentPressException extends RuntimeException {
    public AgentPressException(String message) {
        super(message);
    }
    
    public AgentPressException(String message, Throwable cause) {
        super(message, cause);
    }
}

// Tool execution exceptions
public class ToolExecutionException extends AgentPressException {
    private final String toolName;
    
    public ToolExecutionException(String toolName, String message) {
        super("Error executing tool '" + toolName + "': " + message);
        this.toolName = toolName;
    }
    
    public String getToolName() {
        return toolName;
    }
}

// LLM service exceptions
public class LlmServiceException extends AgentPressException {
    private final String model;
    
    public LlmServiceException(String model, String message) {
        super("Error from
