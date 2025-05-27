# Sandbox and Tool Architecture

This document provides an in-depth technical explanation of the Sandbox and Tool architecture in the Nubian.so Java Agent. It covers how sandboxes are provisioned, how tools are implemented and executed, and how the agent interacts with external systems.

## 1. Sandbox Architecture Overview

### 1.1 Core Components

The sandbox system consists of several key components:

1. **SandboxService**: Core service for sandbox operations and management
2. **SandboxFileService**: Handles file operations within sandboxes
3. **SandboxToolBase**: Base class for all sandbox tools
4. **HttpClientService**: Handles HTTP communications with the Daytona API
5. **WorkspaceService**: Manages workspace resources within sandboxes
6. **ProcessService**: Handles command execution within sandboxes

### 1.2 High-Level Architecture

```
┌───────────────────┐     ┌─────────────────┐     ┌────────────────────┐
│                   │     │                 │     │                    │
│  AgentController  │────▶│  SandboxService │────▶│  HttpClientService │──┐
│                   │     │                 │     │                    │  │
└───────────────────┘     └─────────────────┘     └────────────────────┘  │
                                 │                                         │
                                 ▼                                         │
┌───────────────────┐     ┌─────────────────┐                             │
│                   │     │                 │                             │
│  SandboxToolBase  │◀────│ Tool Registry   │                             │
│                   │     │                 │                             │
└───────────────────┘     └─────────────────┘                             │
         │                                                                │
         ▼                                                                ▼
┌────────────────────────────────┐                         ┌────────────────────┐
│                                │                         │                    │
│ FileTool   ProcessTool   Etc.  │────────────────────────▶│   Daytona API      │
│                                │                         │                    │
└────────────────────────────────┘                         └────────────────────┘
```

## 2. Sandbox Service Implementation

### 2.1 Core SandboxService

The `SandboxService` handles the lifecycle of sandboxes:

- **Dependencies**: Requires `HttpClientService`, `DBConnection`, and `ObjectMapper`
- **Configuration**: Uses Daytona API URL and API key from application properties
- **Key Methods**:
  - `createSandbox`: Creates a new sandbox for a project
  - `getSandbox`: Retrieves sandbox details by ID
  - `executeCommand`: Runs a command in a sandbox
  - `deleteSandbox`: Removes a sandbox when no longer needed

This service communicates with the Daytona API to provision and manage sandbox environments. When creating a sandbox, it stores the sandbox information in the project record in the database, ensuring the sandbox can be accessed later by tools and other services.

### 2.2 SandboxFileService Implementation

The `SandboxFileService` handles file operations within sandboxes:

- **Dependencies**: Requires `HttpClientService` and `ObjectMapper`
- **Configuration**: Uses Daytona API URL and API key from application properties
- **Key Methods**:
  - `readFile`: Reads file content from a sandbox
  - `writeFile`: Writes content to a file in a sandbox
  - `listFiles`: Lists files in a sandbox directory
  - `deleteFile`: Removes a file from a sandbox

This service provides file system operations for the agent, allowing it to read, write, and manipulate files within the sandbox environment. It handles path encoding and proper error handling for all file operations.

## 3. Tool Architecture

### 3.1 SandboxToolBase Class

All sandbox tools extend `SandboxToolBase`, which provides common functionality:

- **Properties**:
  - `projectId`: The ID of the project the tool operates on
  - Service dependencies: `SandboxService`, `SandboxFileService`, etc.
- **Key Methods**:
  - Setter methods for dependency injection
  - `getSandboxFromProject`: Retrieves sandbox details from project record

The `SandboxToolBase` class handles common operations like retrieving sandbox credentials and managing service dependencies. It defines a `SandboxDetails` inner class to encapsulate sandbox ID, password, and organization ID.

When retrieving sandbox details, it attempts to get the information from the project record in the database. If not found, it falls back to a default sandbox ID based on the project ID.

### 3.2 FileTool Implementation

The `FileTool` provides file system operations to the agent:

- **Methods**:
  - `readFile`: Reads content from a file
  - `writeFile`: Writes content to a file
  - `listFiles`: Lists files in a directory
  - `deleteFile`: Deletes a file
  - `createDirectory`: Creates a directory
  - `searchFiles`: Searches for files matching a pattern

- **Schema Definition**:
  - Provides OpenAPI schemas for each method
  - Defines required parameters and their types
  - Includes descriptions for methods and parameters

The `FileTool` follows a consistent pattern for all operations:
1. Retrieves sandbox details using `getSandboxFromProject`
2. Calls the appropriate method on `SandboxFileService`
3. Handles success and error cases with proper logging
4. Returns formatted results or error messages

For operations not directly supported by the `SandboxFileService` (like creating directories or searching), it uses `executeCommand` from the `SandboxService` to run shell commands.

### 3.3 ProcessTool Implementation

The `ProcessTool` allows the agent to execute commands:

- **Methods**:
  - `executeCommand`: Executes a command in the sandbox
  - Overloaded version with working directory parameter
  - `startLongRunningProcess`: Starts a background process

- **Schema Definition**:
  - Provides OpenAPI schemas for each method
  - Defines command parameters and optional working directory
  - Includes descriptions for different execution modes

The `ProcessTool` allows the agent to run shell commands and processes within the sandbox. It handles command execution, captures output and exit codes, and formats the results for the agent to interpret.

Long-running processes are managed through Daytona's process session API, allowing the agent to start services or background tasks that continue running even after the command completes.

### 3.4 ExposeTool Implementation

The `ExposeTool` provides port exposure and URL generation:

- **Methods**:
  - `exposePort`: Makes a sandbox port accessible externally
  - `getPreviewUrl`: Generates a URL for accessing exposed ports

- **Schema Definition**:
  - Defines port parameters and exposure options
  - Specifies return value formats for URLs

This tool allows the agent to expose services running in the sandbox (like web servers) to the outside world, and generate URLs that can be used to access these services. It's essential for demonstrating web applications or services created by the agent.

### 3.5 WorkspaceTool Implementation

The `WorkspaceTool` manages workspace operations:

- **Methods**:
  - `createWorkspace`: Creates a new workspace
  - `cloneRepository`: Clones a Git repository into the workspace
  - `installDependencies`: Installs project dependencies

- **Schema Definition**:
  - Parameters for workspace creation and repository operations
  - Options for dependency management

This tool handles higher-level operations related to workspace setup and management, allowing the agent to create development environments, clone code, and prepare projects for use.

## 4. Tool Registration and Discovery

### 4.1 ToolRegistry Integration

Tools are registered with the `ToolRegistry` at runtime:

- Registration occurs in `AgentRunnerService.executeAgentRun`
- Each tool is instantiated with the project ID
- Required services are injected after instantiation
- Tools are registered with the registry using `registerTool`

### 4.2 Schema Extraction

Tool schemas are extracted and made available to the LLM:

- Each tool implements `getSchemas()` to define its capabilities
- Schemas can be OpenAPI or XML format
- The `ToolRegistry` collects all schemas during registration
- `getOpenApiSchemas()` and `getXmlSchemas()` provide schemas to the LLM

### 4.3 Method Invocation

When the LLM calls a tool, the `ToolRegistry` handles method invocation:

- Tool method is located by name
- Arguments are converted to expected types
- Method is invoked via reflection
- Results are converted back to string or serializable format
- Error handling ensures robust execution

## 5. Sandbox Environment Management

### 5.1 Sandbox Lifecycle

Sandboxes follow a defined lifecycle:

1. **Creation**: Initiated by `AgentController.startAgent()`
2. **Configuration**: Files uploaded, environment variables set
3. **Active Phase**: Used by tools for agent operations
4. **Cleanup**: Eventual deletion by cleanup jobs

### 5.2 Resource Management

The sandbox system includes resource management:

- File storage with quotas and cleanup
- Process monitoring and termination
- Port allocation and release
- Memory and CPU limits

### 5.3 Security Considerations

Security is handled through several mechanisms:

- Isolated execution environments
- Access control through API keys
- Resource limits to prevent abuse
- Network isolation for sensitive operations

## 6. Integration with Daytona

### 6.1 Daytona API Interface

The system interfaces with Daytona through its HTTP API:

- Workspace management (create, delete)
- File operations (read, write, list)
- Command execution and process management
- Port exposure and URL generation

### 6.2 Data Models

Key data models for Daytona integration:

- **DaytonaWorkspace**: Represents a sandbox environment
- **DaytonaCommandExecution**: Result of command execution
- **DaytonaProcessSession**: Long-running process handle
- **FileInfo**: Metadata about files in the sandbox

### 6.3 Error Handling

The integration includes robust error handling:

- Network error detection and logging
- API error status interpretation
- Fallback mechanisms for transient failures
- Detailed error reporting for diagnostics
