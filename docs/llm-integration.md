# LLM Integration

This document provides a detailed technical overview of how the Nubian.so Java Agent integrates with Large Language Models (LLMs). It covers the service architecture, request/response handling, streaming implementation, model configuration, and billing aspects.

## 1. LLM Service Architecture

### 1.1 Core Components

The LLM integration is built around several key components:

1. **LlmService Interface**: A common interface that defines the contract for LLM interactions
2. **OpenAILlmService**: The primary implementation that communicates with OpenAI's API
3. **LlmServiceFactory**: Factory that creates appropriate LLM service implementations
4. **LlmServiceAdapter**: Adapter pattern implementation that normalizes various LLM provider APIs
5. **ResponseProcessor**: Processes raw LLM responses into usable format with tool calls
6. **BillingServiceFacade**: Tracks token usage and costs for billing purposes

### 1.2 High-Level Architecture

```
┌───────────────────┐     ┌─────────────────┐     ┌────────────────────┐
│                   │     │                 │     │                    │
│  ThreadManager    │─────▶  LlmService     │─────▶  OpenAILlmService  │
│                   │     │   Interface     │     │                    │
└───────────────────┘     └─────────────────┘     └────────────────────┘
                                                          │
                                                          ▼
┌───────────────────┐     ┌─────────────────┐     ┌────────────────────┐
│                   │     │                 │     │                    │
│ ResponseProcessor │◀────│ LlmResponse &   │◀────│  OkHttp Client     │
│                   │     │ LlmMessage      │     │                    │
└───────────────────┘     └─────────────────┘     └────────────────────┘
        │                                                  │
        ▼                                                  ▼
┌───────────────────┐                           ┌────────────────────┐
│                   │                           │                    │
│  ToolRegistry     │                           │ BillingService     │
│                   │                           │                    │
└───────────────────┘                           └────────────────────┘
```

## 2. LLM Service Interface

The LlmService interface defines a contract for different LLM provider implementations. It declares a makeLlmApiCall method that accepts all necessary parameters for communicating with an LLM API, including messages, model selection, temperature, token limits, tool schemas, streaming options, and tracking parameters for billing. The method returns a CompletableFuture that resolves to an LLM response, allowing for asynchronous execution.

## 3. OpenAI Implementation

### 3.1 Configuration and Initialization

The OpenAILlmService is a Spring service that implements the LlmService interface for communicating with OpenAI's API. It's configured with API credentials and endpoints through Spring's @Value annotation. The service uses OkHttpClient for HTTP communication, Jackson's ObjectMapper for JSON processing, and a BillingServiceFacade for tracking usage costs. Dependencies are injected through Spring's constructor injection.

### 3.2 Request Construction

When preparing a request to the OpenAI API, the service constructs a request body with all necessary parameters:

1. Basic parameters like model, temperature, max_tokens, and stream flag
2. Formatted messages converted from the internal LlmMessage format to OpenAI's expected format
3. Special handling for different content types, including strings and multimodal content
4. Optional fields like name and tool_call_id for specific message types
5. Tool schemas that define available tools, with tool_choice set to "auto"
6. Function schemas for backward compatibility with older API versions

This method ensures that all messages and parameters are properly formatted according to OpenAI's API specifications.

### 3.3 Making the API Call

The makeLlmApiCall method implements the LlmService interface to make actual API calls to OpenAI:

1. It wraps the entire operation in CompletableFuture.supplyAsync() for asynchronous execution
2. Creates the request body with all necessary parameters
3. Logs the API call at debug level (avoiding sensitive information in production logs)
4. Builds an HTTP request with proper headers, including authentication
5. Executes the request and handles the response
6. Provides specialized handling for streaming vs. non-streaming responses
7. Includes comprehensive error handling for API errors and exceptions
8. Returns either parsed API responses or streaming responses based on the stream parameter

This method serves as the core communication point with the OpenAI API, handling all the details of HTTP requests, authentication, and response processing.

### 3.4 Response Parsing

Response parsing is a critical part of the OpenAI integration:

1. The parseApiResponse method:
   - Extracts the response body and converts it to a JSON object
   - Navigates the JSON structure to find the message content and metadata
   - Constructs a LlmResponse object with the model name and content
   - Handles tool calls by extracting and parsing them
   - Supports backward compatibility with function calls
   - Records usage statistics for billing purposes

2. The parseToolCalls method:
   - Takes a list of tool call objects from the API response
   - Converts each to an internal ToolCall representation
   - Extracts the ID, type, and function details
   - Handles the function name and arguments specifically
   - Returns a list of structured ToolCall objects

Together, these methods transform the raw API response into structured objects that can be used by the rest of the application.

## 4. Streaming Implementation

### 4.1 Streaming Response Handling

For streaming responses, the service uses a specialized handler:

1. The handleStreamingResponse method:
   - Processes Server-Sent Events (SSE) from the OpenAI API
   - Reads and accumulates chunks as they arrive
   - Maintains builders for constructing tool calls incrementally
   - Tracks the model and other metadata
   - Handles different types of deltas (content, tool calls)
   - Detects completion markers and finish reasons
   - Builds a final LlmResponse when streaming is complete
   - Includes error handling for parsing issues and IO exceptions

2. The streaming flow:
   - Processes SSE lines that start with "data: "
   - Handles special markers like "[DONE]"
   - Extracts and accumulates content deltas
   - Builds tool calls incrementally as parts arrive
   - Finalizes tool calls when finish_reason indicates completion
   - Estimates token usage for billing (since streaming responses don't include usage stats)

This approach enables real-time processing of LLM outputs, allowing for immediate updates to the client as the model generates text.

### 4.2 Reactive Streaming Implementation

For a truly reactive streaming implementation, the service uses Spring WebFlux:

1. The streamLlmApiCall method:
   - Creates a Flux using the create() operator
   - Prepares the request body with streaming enabled
   - Sets up an asynchronous HTTP request with OkHttp's enqueue method
   - Handles success and failure callbacks
   - Processes the streaming response line by line
   - Parses and emits chunks as they arrive
   - Completes the stream when the "[DONE]" marker is received

2. The parseStreamingChunk method:
   - Converts raw JSON chunks to structured LlmResponseChunk objects
   - Extracts the model information if available
   - Processes choice deltas for content and tool calls
   - Creates structured Delta objects for tool calls
   - Marks tool calls as finished when the finish_reason indicates completion
   - Returns a complete chunk object for each delta

This reactive approach integrates well with Spring WebFlux applications, allowing for non-blocking processing of streaming responses all the way from the LLM to the client.

## 5. Model Configuration and Selection

### 5.1 Model Resolution

Model resolution is an important part of the service, handling various ways users might specify models:

1. Default handling:
   - If no model is specified, uses "gpt-4o-mini" as the default
   - Maintains a list of officially supported models

2. Model name normalization:
   - Accepts variations of model names (with or without hyphens, case insensitive)
   - Maps unofficial shorthand names to their official equivalents
   - Converts variations like "gpt4" to "gpt-4-turbo" or "haiku" to "claude-3-haiku"

3. Fallback behavior:
   - If an unknown model is requested, logs a warning
   - Returns the default model as a fallback
   - Provides clear logging about the substitution

This approach makes the service more user-friendly while ensuring that only supported models are used.

### 5.2 Model-Specific Configurations

Different LLM providers may require specific parameter transformations:

1. The applyModelSpecificConfig method:
   - Modifies the request body based on the target model
   - Handles Claude-specific parameters for Anthropic models
   - Renames parameters as needed (e.g., max_tokens to max_tokens_to_sample)
   - Reformats tool definitions to match the provider's expected format

2. The convertToolsForClaude method:
   - Transforms OpenAI-style tool definitions to Claude's format
   - Maps function names, descriptions, and parameter schemas
   - Handles differences in parameter naming and structure
   - Ensures compatibility with Claude's API expectations

This flexibility allows the agent to work with multiple LLM providers while maintaining a consistent interface for the rest of the application.

## 6. Billing and Usage Tracking

### 6.1 Token Counting and Billing

Usage tracking is critical for billing and resource management:

1. The BillingServiceFacade:
   - Records token usage for each LLM call
   - Calculates costs based on model-specific pricing
   - Maintains usage records for billing periods
   - Provides methods to query usage statistics

2. Usage recording process:
   - Extracts token counts from LLM response usage data
   - For streaming responses, estimates token usage
   - Associates usage with user ID, run ID, and timestamp
   - Calculates cost based on prompt and completion tokens
   - Persists usage records in the database

3. Cost calculation:
   - Uses model-specific rates for prompt and completion tokens
   - Accounts for different pricing tiers and models
   - Handles special cases like summarization or system messages
   - Provides accurate billing information for user invoicing

This comprehensive tracking ensures that usage is accurately measured and billed, while also providing valuable insights into resource utilization.

## 7. Error Handling and Resilience

The LLM integration includes robust error handling:

1. API error handling:
   - Detects and logs HTTP errors from the LLM provider
   - Includes detailed error information for debugging
   - Provides appropriate error messages for client feedback
   - Implements retry logic for transient errors

2. Timeout handling:
   - Sets appropriate timeouts for API calls
   - Detects and handles connection timeouts
   - Provides fallback behavior for timeout scenarios
   - Logs timeout information for monitoring

3. Resilience patterns:
   - Uses circuit breakers for API call protection
   - Implements backoff strategies for retries
   - Provides graceful degradation options
   - Ensures system stability during API outages

These measures ensure that the system remains operational even when external services experience issues, providing a reliable experience for users.

## 8. Security Considerations

Security is a critical aspect of LLM integration:

1. API key management:
   - Securely stores API keys in environment variables
   - Never logs or exposes keys in responses
   - Rotates keys periodically for security
   - Uses separate keys for different environments

2. Data protection:
   - Avoids sending sensitive information to the LLM
   - Implements content filtering for user inputs
   - Sanitizes responses to prevent data leakage
   - Logs minimal information for compliance

3. User authentication:
   - Verifies user permissions before making API calls
   - Associates usage with specific user accounts
   - Enforces rate limits and quotas per user
   - Prevents unauthorized access to LLM capabilities

These security measures protect both the system and user data while allowing appropriate access to LLM functionality.
