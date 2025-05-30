# Tool Usage in AgentPress Demo

This document details the tools configured and potentially used within the `AgentPressDemo`, how they are registered, and how they would be invoked by the LLM.

## Registered Tools

The `AgentPressDemo` specifically registers the `WebSearchTool`. This tool provides functionalities related to web searching and information retrieval.

```java
// In AgentPressDemo.java (conceptual)
toolRegistry.registerTool(webSearchTool, null); // Registers all methods of webSearchTool
```

The `ToolRegistry` processes this tool and makes its functions available to the LLM.

## Available Tool Functions from `WebSearchTool`

The `WebSearchTool` exposes the following functions to the LLM, as seen in the startup logs:

```
INFO ... c.s.ai.agentpress.service.ToolRegistry : Retrieved 2 OpenAPI schemas after filtering. Content: [
    {
        name=search, 
        description=Search the web for information, 
        parameters={type=object, properties={query={type=string, description=The search query}}, required=[query]}
    }, 
    {
        name=getInfo, 
        description=Get detailed information about a topic, 
        parameters={type=object, properties={topic={type=string, description=The topic to get information about}, maxResults={type=integer, description=The maximum number of results to return, default=3}}, required=[topic]}
    }
]
```

### 1. `search`

*   **Description**: "Search the web for information"
*   **Parameters**:
    *   `query` (string, required): The search query.
*   **How it's called (OpenAI native tool call format)**:
    If the LLM decides to use this tool, it would generate a tool call in its response, similar to this:
    ```json
    {
      "tool_calls": [
        {
          "id": "call_abc123",
          "type": "function",
          "function": {
            "name": "search",
            "arguments": "{\"query\": \"latest AI advancements\"}"
          }
        }
      ]
    }
    ```
*   **Execution**:
    *   The `ResponseProcessor` would identify this tool call.
    *   It would look up "search" in the `ToolRegistry` and find the `WebSearchTool.search(String query)` method.
    *   The `arguments` JSON string (`{"query": "latest AI advancements"}`) would be parsed, and the value for "query" would be passed to the method.
*   **Output (Current Mock Implementation)**:
    The `WebSearchTool.search` method now returns a dummy success response:
    ```java
    // In WebSearchTool.java
    logger.info("Performing web search for: {}", query);
    return CompletableFuture.supplyAsync(() -> {
        if ("What is the capital of France?".equalsIgnoreCase(query)) {
            return successResponse("Paris is the capital of France.");
        }
        return successResponse("Dummy search result for query: " + query);
    });
    ```
    If the query is "What is the capital of France?", the `ToolResult` will be `ToolResult(success=true, output="Paris is the capital of France.")`. Otherwise, it provides a generic dummy result. This result is then formatted and sent back to the LLM.

### 2. `getInfo`

*   **Description**: "Get detailed information about a topic"
*   **Parameters**:
    *   `topic` (string, required): The topic to get information about.
    *   `maxResults` (integer, optional, default: 3): The maximum number of results to return.
*   **How it's called (OpenAI native tool call format)**:
    ```json
    {
      "tool_calls": [
        {
          "id": "call_xyz789",
          "type": "function",
          "function": {
            "name": "getInfo",
            "arguments": "{\"topic\": \"Quantum Computing\", \"maxResults\": 5}" 
            // or just "{\"topic\": \"Quantum Computing\"}" using the default for maxResults
          }
        }
      ]
    }
    ```
*   **Execution**:
    *   `ResponseProcessor` identifies the call.
    *   `ToolRegistry` maps "getInfo" to `WebSearchTool.getInfo(Map<String, Object> params)`.
    *   The `arguments` JSON is parsed into a `Map`, and this map is passed to the method.
*   **Output (Current Mock Implementation)**:
    The `WebSearchTool.getInfo` method now returns a dummy success response:
    ```java
    // In WebSearchTool.java
    logger.info("Getting info about: {} (max results: {})", topic, maxResults);
    return CompletableFuture.supplyAsync(() -> {
        if ("France".equalsIgnoreCase(topic)) {
            return successResponse("France is a country in Western Europe. Its capital is Paris. Max results: " + maxResults);
        }
        return successResponse("Dummy information for topic: " + topic + ", max results: " + maxResults);
    });
    ```
    If the topic is "France", the `ToolResult` will be `ToolResult(success=true, output="France is a country in Western Europe. Its capital is Paris. Max results: [maxResultsValue]")`. Otherwise, it provides generic dummy information.

## Demo Run Observations

In the provided logs for the demo run (`AgentPressDemo`), the final output includes:
```
INFO ... agentpress.com.nubian.ai.AgentPressDemo : Assistant Tool Calls: null
```
This indicates that for the specific user message in that demo ("What is the capital of France?"), the LLM (gpt-4o) decided to answer directly without using any of the available tools (`search` or `getInfo`). If the query had been more complex or explicitly asked for a web search, the LLM might have chosen to use one of these tools, and the flow described above would have been triggered.

## XML Tool Calling

Both `search` and `getInfo` are also annotated with `@XmlSchema`. If the system were configured for XML tool calling (i.e., `ProcessorConfig.isXmlToolCalling()` is true and `ProcessorConfig.isNativeToolCalling()` is false), the LLM would be instructed to format its tool calls in XML within its text response.

*   **`search` XML call example**:
    ```xml
    <search>What is the capital of France?</search>
    ```
*   **`getInfo` XML call example**:
    ```xml
    <get-info maxResults="5">
        <topic>Spring Boot</topic>
    </get-info>
    ```
The `ResponseProcessor` would then parse this XML from the assistant's message content to identify and execute the tool calls. The execution and mock output would be the same as described for native tool calls.
