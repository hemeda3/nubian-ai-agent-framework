# Request Flow End-to-End

This document provides a detailed technical walkthrough of how a request flows through the Nubian.so Java Agent system, from initial client request to final response. This covers the entire lifecycle including authentication, execution, streaming, and all the underlying technical implementations.

## 1. Client Request Initiation

### 1.1 HTTP Request Entry Point

The client initiates a request to start an agent run by sending a `POST` request to the `/api/agent/runs` endpoint handled by `AgentController.startAgent()`. The request includes:

- **Authorization Header**: JWT token for authentication
- **Request Body**: `AgentRunRequest` JSON payload with agent configuration
- **Optional File Uploads**: Files to be made available in the agent's sandbox

**Example Request:**
- POST to `/api/agent/runs`
- Headers include Authorization (JWT token) and Content-Type
- Request body contains model specifications, initial prompt, and configuration
- May include file uploads to be made available in the agent's sandbox

## 2. Authentication & Authorization Flow

### 2.1 JWT Token Validation

`JwtUtils.getUserIdFromToken()` parses and validates the JWT token:

1. Extracts the token from the `Authorization` header
2. Validates signature, expiry, and issuer claims
3. Returns the `userId` from the JWT payload
4. Throws `ResponseStatusException` with appropriate status codes for failures

### 2.2 Authorization Checks

After validating the token, `AuthUtils` verifies permissions:

1. `AuthUtils.verifyProjectAccess()` checks if the user has access to the project
2. `AuthUtils.isAccountAdmin()` verifies admin-level operations
3. Access checks query the database with RLS policies through `DBConnection`

## 3. Orchestration & Resource Initialization

### 3.1 Account & Project Resolution

`AgentController.startAgent()` orchestrates the initial setup:

1. `accountService.getOrCreateAccountForUser(userId)` retrieves or creates the user's account asynchronously
2. `projectService.getOrCreateProjectForAccount(accountId, userId)` retrieves or creates a project for this account asynchronously
3. Both operations involve database transactions via `DBConnection` and return `CompletableFuture<>` results that must be joined

### 3.2 Sandbox Provisioning

`SandboxService.createSandbox()` provisions an isolated environment:

1. Calls the Daytona API via `HttpClientService` to create a workspace
2. Returns `DaytonaWorkspace` with sandboxId and other metadata
3. Updates the project record with the sandbox details via `projectService.updateProjectSandboxId()`

The controller then calls the SandboxService to create a sandbox, retrieves the sandbox ID, and updates the project record with this information.

### 3.3 Thread Creation

`ThreadManager.createThread()` initializes a new conversation thread:

1. Generates a UUID for the thread
2. Inserts thread metadata in the database with project and account associations
3. Returns a `Thread` object with the new ID

### 3.4 File Upload Handling

For multipart requests containing files:

1. Files are extracted from the `MultipartFile[]` array
2. Each file is uploaded to the sandbox via `sandboxFileService.uploadFile(sandboxId, path, bytes).join()`
3. File paths in the sandbox are tracked and appended to the initial prompt

## 4. Agent Run Submission

### 4.1 Initial Message Creation

`ThreadManager.addMessage()` adds the user's initial prompt to the thread:

1. Creates a new message with type "user" in the database
2. The message includes the initial prompt text plus any uploaded file information
3. Returns the created `Message` object with its ID

### 4.2 Agent Run Record Creation

`AgentBackgroundService.submitAgentRun()` initiates the agent execution:

1. Generates a unique `agentRunId` 
2. Creates an entry in the `agent_runs` table with status "PENDING"
3. Adds the run to a queue for asynchronous execution
4. Returns immediately while processing continues in the background

## 5. Streaming Response Initiation

### 5.1 Server-Sent Events (SSE) Setup

Clients open an SSE connection to `/api/agent/runs/{agentRunId}/stream`:

1. `AgentController.streamAgentRun()` creates a new `SseEmitter` with a long timeout
2. The emitter is registered with `AgentRedisHelper.subscribeToRunStream(agentRunId, messageHandler)`
3. Connection lifecycle events (completion, timeout, error) are handled with appropriate cleanup

The controller creates an SSE emitter with a long timeout and registers it with the AgentRedisHelper to subscribe to the run stream. When messages are received, they're sent through the emitter to the client, with appropriate error handling for connection lifecycle events.

## 6. Agent Execution Loop

### 6.1 Background Processing 

`AgentRunnerService.executeAgentRun()` is the core agent execution function:

1. Retrieves the agent configuration and resources (project, thread, model)
2. Registers tools via `AgentRunManager.registerAgentTools()`
3. Enters a loop that continues until completion or interruption

### 6.2 Tool Registration

`ToolRegistry` dynamically registers available tools:

1. `ToolRegistry.registerTool()` registers each tool with its schema
2. XML and OpenAPI schemas are extracted and stored
3. Tools are organized by name and mapping to java methods via reflection
4. Returns a complete registry of available tools for the agent

### 6.3 The Main Agent Loop

Each iteration of the agent loop follows this process:

Each iteration of the agent loop follows this process:
1. Check for todo.md updates and other control signals
2. Manage context window using summarization if needed
3. Construct the message for this iteration
4. Execute one turn of LLM reasoning via AgentExecutionHelper
5. Process results, detect completion or errors
6. Stream results to client via Redis

## 7. LLM Interaction

### 7.1 Conversation and Tool Execution Flow

`AgentExecutionHelper.executeIteration()` handles each LLM turn:

1. Constructs the LLM prompt with messages, system instructions, and tools
2. Calls `ThreadManager.runThread()` which orchestrates the LLM call and tool execution
3. Receives, processes, and potentially streams responses

### 7.2 LLM API Call

`OpenAILlmService.makeLlmApiCall()` handles communication with the LLM API:

1. Formats the request body with messages, tools, and parameters
2. Makes an HTTP request to the LLM provider (OpenAI) via OkHttp
3. For streaming responses, processes chunks as they arrive
4. Records usage and costs via `BillingServiceFacade`

The OpenAILlmService formats the request body with messages, tools, and parameters, then makes an HTTP request to the LLM provider. For streaming responses, it processes chunks as they arrive, while for standard responses, it parses the complete response and records usage costs.

### 7.3 Response Processing

`ResponseProcessor` parses the LLM responses and tool calls:

1. For streaming responses, accumulates text and detects tool calls incrementally
2. For tool calls, extracts parameters using XML or JSON parsing
3. Handles multi-modal content and specialized formats

## 8. Tool Execution

### 8.1 Tool Call Processing

When a tool call is detected:

1. `ToolRegistry.getTool()` or `getXmlTool()` retrieves the tool by name
2. `ToolRegistry.invokeMethod()` calls the appropriate method with arguments
3. Arguments are converted from Maps to proper types using Jackson's `ObjectMapper`
4. Results are converted back to JSON for the LLM's consumption

### 8.2 Sandbox Tool Execution

Tools that interact with the sandbox environment follow a pattern:

1. `SandboxToolBase` provides common functionality to all sandbox tools
2. `SandboxToolBase.getSandboxFromProject()` retrieves sandbox credentials
3. Tool-specific functions make HTTP calls to the Daytona API
4. Results are formatted for LLM consumption

Tools that interact with the sandbox environment follow a common pattern:
1. They retrieve sandbox credentials via SandboxToolBase
2. Make API calls to the Daytona service through specific service classes
3. Handle successful responses and errors appropriately
4. Format results for LLM consumption

## 9. Real-time Updates and Streaming

### 9.1 Redis Streaming

`AgentRedisHelper` handles real-time streaming to clients:

1. `publishMessageToRedisStream()` publishes messages to Redis channels
2. Redis Pub/Sub enables real-time transmission of agent messages
3. Redis Streams enable persisted, ordered message delivery

The AgentRedisHelper publishes messages to Redis channels and persists them in Redis streams, enabling real-time transmission of agent messages to subscribed clients.

### 9.2 Stream Handling

For streaming responses from the LLM:

1. `OpenAILlmService` processes the streamed chunks incrementally
2. `ResponseProcessor.processStreamingResponse()` accumulates tokens and detects tool calls
3. Partial updates are published to Redis for real-time client updates

## 10. Completion and Cleanup

### 10.1 Detecting Completion

`AgentRunManager.isAgentTaskComplete()` determines when the agent has finished:

1. Checks for explicit completion signals in messages
2. Monitors for timeouts or maximum iterations
3. Detects terminal error conditions

### 10.2 Finalizing the Run

When the agent completes:

1. `AgentBackgroundService.updateAgentRunStatus()` updates the status to "COMPLETED"
2. Any final responses are sent to the client via Redis
3. Resources are marked for cleanup or preservation as needed

### 10.3 Error Handling

If errors occur during execution:

1. Exceptions are caught and logged at appropriate levels
2. `AgentBackgroundService.updateAgentRunStatus()` updates status to "FAILED" with error details
3. Error messages are streamed to the client
4. Resources are cleaned up appropriately

## 11. Database Interaction Pattern

Throughout the entire flow, the application uses an asynchronous database interaction pattern:

Throughout the entire flow, the application uses an asynchronous database interaction pattern, leveraging CompletableFuture with thenApply, thenCompose, and exceptionally methods to manage complex async flows.

## 12. Complete Request-Response Cycle Diagram

```
Client                               AgentController                    AgentRunnerService                   OpenAILlmService                     Tools
  |                                        |                                   |                                   |                                  |
  |-- POST /api/agent/runs --------------->|                                   |                                   |                                  |
  |                                        |                                   |                                   |                                  |
  |                                        |-- Auth & validate ---------------+|                                   |                                  |
  |                                        |-- Create account/project --------+|                                   |                                  |
  |                                        |-- Provision sandbox -------------+|                                   |                                  |
  |                                        |-- Create thread -----------------+|                                   |                                  |
  |                                        |-- Upload files ------------------->|                                   |                                  |
  |                                        |                                   |                                   |                                  |
  |<-- 200 OK (agentRunId, threadId) ------|                                   |                                   |                                  |
  |                                        |                                   |                                   |                                  |
  |-- GET /runs/{id}/stream -------------->|                                   |                                   |                                  |
  |                                        |-- Setup SSE emitter ------------->|                                   |                                  |
  |                                        |                                   |                                   |                                  |
  |                                        |-- submitAgentRun --------------->+|                                   |                                  |
  |                                        |                                   |-- executeAgentRun() ------------->|                                   |
  |                                        |                                   |-- Register tools ---------------->|                                   |
  |                                        |                                   |                                   |                                  |
  |                                        |                                   |-- Execute iteration ------------->|                                   |
  |                                        |                                   |                                   |-- LLM API call ----------------->|
  |                                        |                                   |                                   |<-- LLM Response -----------------|
  |                                        |                                   |                                   |                                  |
  |                                        |                                   |                                   |-- Parse tool calls ------------->|
  |                                        |                                   |                                   |                                  |-- Execute tool call ----+
  |                                        |                                   |                                   |                                  |<- Tool result ----------+
  |                                        |                                   |                                   |<-- Tool execution result --------|
  |                                        |                                   |<-- AgentLoopResult ---------------|                                   |
  |                                        |                                   |                                   |                                  |
  |                                        |                                   |-- Publish to Redis ------------->+|                                   |
  |<-- SSE Event (message) ---------------|                                   |                                   |                                  |
  |                                        |                                   |                                   |                                  |
  |                                        |                                   |-- [Repeat loop until complete] -->|                                   |
  |                                        |                                   |                                   |                                  |
  |<-- SSE Event (completion) ------------|                                   |                                   |                                  |
  |                                        |                                   |                                   |                                  |
  +----------------------------------------+-----------------------------------+-----------------------------------+----------------------------------+
```

## 13. Technical Implementation Patterns

### 13.1 Asynchronous Design

The system uses CompletableFuture extensively for non-blocking operations:

1. Database queries return `CompletableFuture<List<Map<String, Object>>>` 
2. External API calls are wrapped in `CompletableFuture<T>`
3. Composition patterns like `thenApply()`, `thenCompose()`, and `exceptionally()` manage complex async flows
4. `.join()` is used strategically when synchronous results are required

### 13.2 Dependency Injection

Spring's dependency injection is used throughout:

1. Controllers, services, and helpers are injected via constructor injection
2. Configuration properties are injected with `@Value` annotations
3. Tools receive service dependencies at registration time

### 13.3 Streaming Pattern

The streaming implementation follows a reactive pattern:

1. LLM responses are processed as they arrive
2. Redis pub/sub enables real-time updates
3. SSE emitters provide a push mechanism to clients

### 13.4 Error Handling

Robust error handling is implemented at multiple levels:

1. Global exception handlers for REST endpoints
2. `CompletableFuture.exceptionally()` for async error handling
3. Fallback mechanisms for degraded but functional operation
4. Detailed logging for observability
