# Agent Execution Loop

This document provides an in-depth technical breakdown of the Agent Execution Loop, which is the core processing engine of the Nubian.so Java Agent. It details how the agent processes requests, interacts with the LLM, executes tools, and manages its execution flow.

## 1. Agent Loop Architecture

### 1.1 Core Components

The agent execution loop involves several key components working together:

1. **AgentRunnerService**: Orchestrates the overall execution flow
2. **AgentExecutionHelper**: Handles individual iteration steps
3. **ThreadManager**: Manages message history and LLM interactions
4. **ContextManager**: Handles context window management and summarization
5. **ToolRegistry**: Provides tool discovery and execution capabilities
6. **ResponseProcessor**: Parses LLM responses and extracts tool calls
7. **LlmService**: Communicates with the language model provider

### 1.2 High-Level Flow Diagram

```
┌─────────────────────────────────────────┐
│          AgentRunnerService             │
│                                         │
│ ┌─────────┐  ┌──────────┐  ┌──────────┐ │
│ │ Init &  │  │ Process  │  │ Finalize │ │
│ │ Setup   ├─►│ Iterations├─►│ Result  │ │
│ └─────────┘  └──────────┘  └──────────┘ │
└───────────────┬─────────────────────────┘
                │
                ▼
┌───────────────────────────────────────────────────────────┐
│                    AgentExecutionHelper                    │
│                                                           │
│  ┌─────────────┐  ┌───────────┐  ┌───────────────────┐    │
│  │ Construct   │  │ Execute   │  │ Process Results   │    │
│  │ Messages    ├─►│ LLM Call  ├─►│ & Tool Execution  │    │
│  └─────────────┘  └───────────┘  └───────────────────┘    │
└───────────────────────────────────────────────────────────┘
```

## 2. AgentRunnerService - The Main Loop

### 2.1 Loop Initialization

The entry point for agent execution initializes key variables and resources, registers tools for the agent run, and starts the execution in a separate thread using CompletableFuture.runAsync(). It handles initialization of the user ID, system prompt, model resolution, and timestamp tracking. It also includes error handling to update the agent run status and log any exceptions.

### 2.2 Tool Registration Process

Tool registration creates and configures various tools the agent can use, including:
- Sandbox tools like FileTool for file operations
- ProcessTool for command execution
- DataProviderTool for external API access
- Other specialized tools as needed

Each tool is instantiated with the project ID, configured with appropriate services, registered with the tool registry, and added to the list of available tools.

### 2.3 The Main Execution Loop

The main execution loop runs until the agent has completed its task or is interrupted. During each iteration, it:

1. Checks for interruption signals
2. Checks for updates to the todo.md file
3. Manages the context window, performing summarization if needed
4. Constructs a temporary message for this iteration
5. Executes one turn of LLM reasoning via the AgentExecutionHelper
6. Processes the results, adding new messages to the collection
7. Streams results to the client via Redis
8. Updates the todo.md file if necessary
9. Checks for completion signals, errors, or continuation flags

Once the loop exits, it updates the final status of the agent run to either STOPPED or COMPLETED.

## 3. Agent Execution Helper - Single Iteration Processing

### 3.1 Iteration Structure

Each iteration of the agent loop is handled by the AgentExecutionHelper.executeIteration() method, which:

1. Retrieves tool schemas and XML examples from the tool registry
2. Configures the processor with settings for native tool calling and XML tool calling
3. Calls ThreadManager.runThread() to execute the iteration with the LLM
4. Processes the resulting messages to check for completion signals or errors
5. Wraps the results in an AgentLoopResult object with messages, termination status, and continuation flags
6. Includes error handling to create appropriate error messages if something goes wrong

The method returns a CompletableFuture that resolves to the AgentLoopResult, allowing for asynchronous execution.

### 3.2 Thread Manager Execution

The ThreadManager.runThread() method is at the heart of each iteration, and performs these key steps:

1. Retrieves the formatted thread messages for context
2. Creates a system message with the provided prompt
3. Adds a user message if there's user input for this iteration
4. Prepends the system message to the conversation history
5. Makes the LLM API call with the prepared messages, model, and configuration
6. Processes the LLM response, extracting content and tool calls
7. Saves all new messages to the thread in the database
8. Returns a CompletableFuture that resolves to the list of messages

This method uses CompletableFuture composition with thenCompose() to chain asynchronous operations together in a clean, readable way.

## 4. Tool Execution Flow

### 4.1 Detecting and Parsing Tool Calls

The ResponseProcessor.processResponse() method handles the LLM response and executes any tool calls:

1. Creates an assistant message with the LLM's response content
2. Processes any tool calls included in the response:
   - Extracts the tool name and arguments
   - Looks up the tool in the registry
   - Executes the tool with the provided arguments
   - Creates a tool result message with the output
   - Handles errors if the tool is not found or execution fails
3. Also processes XML-formatted tool calls if enabled
4. Returns a CompletableFuture with all the resulting messages

This method is responsible for the critical step of converting LLM tool calls into actual tool executions and capturing the results.

### 4.2 XML Tool Call Parsing

XML tool calls are parsed using specialized methods:

1. The extractXmlToolCalls method:
   - Uses regex to find all XML tags in the LLM's response content
   - Extracts the tool name and full XML content for each match
   - Parses the XML to extract parameters
   - Creates a ToolCall object with the extracted information
   - Returns a list of all identified tool calls

2. The parseXmlToolCall method:
   - Extracts the root element name
   - Looks up the XML schema for the tool
   - For each parameter mapping in the schema:
     - Gets the parameter name, node type, and path
     - Extracts the parameter value from the XML content
     - Adds the parameter to the result map
   - Returns a map of parameter names to values

These methods allow the agent to process XML-formatted tool calls, which provide a more structured way for LLMs to specify tool invocations.

## 5. Context Management

### 5.1 Token Counting and Context Window Management

The ContextManager.checkAndSummarizeIfNeeded method manages the context window:

1. Gets the current token count for the thread
2. If the token count is below the threshold, no summarization is needed
3. If summarization is needed:
   - Gets the messages that need to be summarized
   - Creates a summary using the LLM
   - Adds the summary message to the thread
   - Returns true if summarization was performed
4. Includes error handling to log issues and return false if summarization fails

This method ensures that the conversation history stays within the LLM's context window limits by periodically creating summaries of older messages.

### 5.2 Summarization Process

The summarization process creates a concise version of the conversation:

1. Creates a system message with detailed summarization instructions:
   - The summary must preserve key information and decisions
   - Include tool usage and results
   - Maintain chronological order
   - Be structured with clear sections
   - Include only factual information from the conversation
   - Be concise but detailed enough for continuation

2. Makes an LLM API call to generate the summary
   - Uses a special prompt format with clear markers
   - Sets a target token limit for the summary
   - Configures the LLM for summarization (low temperature)

3. Formats the resulting summary with clear delimiters
   - Adds header and footer markers
   - Includes a transition note for context

This approach uses the LLM's own capabilities to create high-quality summaries that preserve the important context while reducing token usage.

## 6. Streaming Response Handling

### 6.1 Streaming Implementation

For streaming responses, the flow changes to process chunks incrementally:

1. The processStreamingResponse method creates a Flux of messages:
   - Maintains accumulators for content and tool call parameters
   - Processes each chunk as it arrives from the LLM
   - Emits message updates as content and tool calls are received
   - Detects when tool calls are complete and ready for execution
   - Executes tools when all parameters have been received
   - Handles errors and completes the stream appropriately

2. For content updates:
   - Detects if this is the first chunk or a continuation
   - Creates appropriate message objects with update flags
   - Accumulates content for later XML tool call detection

3. For tool call updates:
   - Tracks each tool call by ID and index
   - Accumulates tool name and arguments as they arrive
   - Marks tool calls as complete when finished
   - Executes tools when all parameters are available

4. For XML tool calls:
   - Periodically checks accumulated content for XML patterns
   - Extracts and processes any complete XML tool calls
   - Marks them as ready for execution

This streaming approach allows for real-time updates to the client, showing the agent's "thoughts" and tool progress incrementally.

## 7. Todo File Handling

Nubian.so agents maintain a `todo.md` file to track progress:

1. The updateTodoFileFromMessage method:
   - Checks for Markdown code blocks in assistant messages
   - Extracts todo.md content using regex patterns
   - Writes the updated content to the todo.md file in the sandbox
   - Returns success/failure status

2. The todo.md file serves as:
   - A record of completed tasks
   - A list of planned tasks
   - A communication channel between agent iterations
   - A way for users to track progress

This file-based approach provides a persistent record of the agent's work plan and progress across iterations, which is useful for both the agent and human users.
