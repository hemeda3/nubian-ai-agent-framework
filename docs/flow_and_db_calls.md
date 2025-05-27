# End-to-End Request Flow and Database Interactions

This document outlines the typical end-to-end flow of a request within the Nubian AI AgentPress system, highlighting key service interactions and database calls.

## Overview

The system is designed to manage conversational AI interactions, where an "agent" (LLM) can use tools to fulfill user requests. This involves maintaining conversation threads, registering available tools, making calls to LLM providers (like OpenAI), processing responses, and potentially executing tools.

## Core Components Involved:

*   **`AgentPressDemo` / Entry Point**: Initiates a demo run or handles incoming user requests.
*   **`AccountService` / `ProjectService`**: Manage user accounts and projects, often involving initial DB lookups or creations.
*   **`ThreadManager`**: Orchestrates the entire conversation flow for a given thread.
*   **`ToolRegistry`**: Maintains a registry of available tools and their schemas.
*   **`Tool` (and its subclasses like `WebSearchTool`)**: Define the actual tools, their schemas (using annotations like `@OpenApiSchema`, `@XmlSchema`, `@ToolFunction`), and their execution logic.
*   **`LlmServiceFactory` / `OpenAIService` / `GoogleLlmService`**: Handle interactions with the LLM providers.
*   **`OpenAIRequestBuilder` / `OpenAIStreamHandler`**: Construct requests and handle responses from OpenAI.
*   **`ResponseProcessor`**: Processes LLM responses, identifies tool calls, and manages tool execution.
*   **`DBConnection` / `ResilientSupabaseClient` (and specific operation classes like `MessageOperations`, `QueryOperations`, `InsertOperations`)**: Handle all database interactions, primarily with a Supabase (PostgreSQL) backend.

## Detailed Flow:

1.  **Request Initiation (e.g., `AgentPressDemo`)**:
    *   A user request comes in (e.g., a new message to an agent).
    *   **DB Call**: The system may first retrieve or create user `account` and `project` records.
        *   Example: `QueryOperations` on `basejump.accounts` table.
        *   Example: `QueryOperations` on `projects` table.
    *   **DB Call**: A new conversation `thread` is created or an existing one is retrieved.
        *   Example: `InsertOperations` into the `threads` table (fields: `thread_id`, `project_id`, `account_id`, `created_at`, `updated_at`). This involves prior checks for account/project existence.

2.  **Tool Registration (Typically at Startup)**:
    *   Instances of `Tool` subclasses (e.g., `WebSearchTool`) are created (often as Spring beans).
    *   During `Tool` instantiation, the `registerSchemas()` method is called.
        *   This method inspects annotations (`@OpenApiSchema`, `@XmlSchema`, `@ToolFunction`, `@CustomSchema`) on the tool's methods.
        *   It parses these annotations to create `ToolSchema` objects. This involves:
            *   Determining the `name`, `description`, and `parameters` for each tool function.
            *   Sanitizing the `name` to be compliant with LLM provider requirements (e.g., OpenAI's `^[a-zA-Z0-9_-]{1,64}$`).
            *   Using an `ObjectMapper` (if provided to the `Tool` constructor) to parse JSON schema strings (e.g., from `@OpenApiSchema`).
    *   The `ToolRegistry` bean collects these `ToolSchema` objects from all registered `Tool` instances.
        *   `ToolRegistry.registerTool(toolInstance, ...)` is called.
        *   It stores the `ToolSchema` (containing the `schemaMap` with the name, description, parameters) and the tool instance.

3.  **Message Handling and LLM Call (`ThreadManager.runThread`)**:
    *   The new user message is added to the current thread.
    *   **DB Call**: `InsertOperations` (via `DBConnection.insertMessage`) saves the message to a `messages` table (fields likely include `message_id`, `thread_id`, `role`, `content`, `type`, `metadata`, `created_at`).
    *   `ThreadManager` prepares for an LLM call:
        *   Retrieves historical messages for the thread.
        *   **DB Call**: `QueryOperations` (via `DBConnection.getLlmFormattedMessages`) fetches messages, which are then converted to `LlmMessage` objects.
        *   Constructs a system prompt, potentially including XML tool examples if XML tool calling is enabled (`toolRegistry.getXmlExamples()`).
        *   Retrieves OpenAPI tool schemas from `ToolRegistry.getOpenApiSchemas()`. This list of `Map<String, Object>` (where each map is a function definition with `name`, `description`, `parameters`) is passed to the LLM service.
    *   An appropriate `LlmService` (e.g., `OpenAIService`) is obtained from `LlmServiceFactory`.
    *   `LlmService.makeLlmApiCall` is invoked.
        *   This typically delegates to `OpenAIRequestBuilder.buildRequestBody` (or a stream handler).
        *   `OpenAIRequestBuilder` constructs the JSON payload for the OpenAI API, including the `messages` array and the `tools` array (derived from the `toolFunctionDefinitions` list).
        *   **Crucial Check**: `OpenAIRequestBuilder` performs a final validation/fallback for each tool's `name`, `description`, and `parameters` in the `toolFunctionDefinitions` before adding them to the request.
        *   The request is sent to the LLM API.

4.  **Processing LLM Response (`ResponseProcessor`)**:
    *   The LLM's response is received (either streamed or as a single payload).
    *   **DB Call**: The raw assistant message (and any tool calls) is saved to the `messages` table.
    *   `ResponseProcessor` parses the response:
        *   If the response contains tool calls (either native JSON or XML in the content):
            *   It identifies the tool name and arguments.
            *   It looks up the tool instance and method in the `ToolRegistry`.
            *   It executes the tool method (e.g., `WebSearchTool.search(...)`).
            *   **DB Call**: The result of the tool execution is saved as a new message in the `messages` table (typically with `role: tool`).
            *   If auto-continue is enabled, the flow may loop back to step 3.c (LLM call with tool results).
        *   If the response is a final text answer, it's saved.

5.  **Context Management (`ContextManager`)**:
    *   Periodically, or before an LLM call if the token count is high:
    *   **DB Call**: `ContextManager` may retrieve messages to calculate token count.
    *   If summarization is needed, it makes an LLM call to summarize older messages.
    *   **DB Call**: The summary message is saved to the `messages` table.

## Key Database Tables (Inferred):

*   `basejump.accounts`: Stores account information.
*   `projects`: Stores project information, linked to accounts.
*   `threads`: Stores conversation thread metadata, linked to projects and accounts.
    *   `thread_id` (PK)
    *   `project_id` (FK)
    *   `account_id` (FK)
    *   `created_at`, `updated_at`
*   `messages`: Stores individual messages within threads.
    *   `message_id` (PK)
    *   `thread_id` (FK)
    *   `role` (e.g., 'user', 'assistant', 'tool', 'system')
    *   `content` (JSONB or TEXT, storing text, tool calls, tool results)
    *   `type` (e.g., 'text', 'tool_call', 'tool_result', 'status', 'summary')
    *   `is_llm_message` (boolean)
    *   `metadata` (JSONB)
    *   `created_at`

This flow ensures that conversations are persisted, tools are correctly defined and made available to the LLM, and interactions are managed systematically.
