# Nubian.so Java Agent Technical Documentation

This documentation provides in-depth technical details about the Nubian.so Java Agent implementation, focusing on architecture, request flows, and key components.

## Table of Contents

1. [Architecture Overview](architecture-overview.md)
2. [Request Flow End-to-End](request-flow.md)
3. [Agent Execution Loop](agent-execution-loop.md)
4. [Sandbox and Tool Architecture](sandbox-tool-architecture.md)
5. [Billing, Accounts, Projects, and Subscriptions](billing-accounts-projects.md)
6. [Context Management and Token Handling](context-management.md)
7. [LLM Integration](llm-integration.md)
8. [Streaming and Real-time Updates](streaming.md)
9. [Authentication and Authorization](auth.md)
10. [Database Schema and Access Patterns](database.md)
11. [Redis Usage for Pub/Sub and Caching](redis-usage.md)

## System Glossary

- **Agent**: An autonomous AI entity capable of executing tasks using tools and LLM capabilities
- **Thread**: A sequence of messages, tool calls, and results that represent a conversation
- **Project**: A container for related threads and resources
- **Sandbox**: An isolated execution environment where agent code and tools run securely
- **Tool**: A function with well-defined inputs and outputs that agents can use to interact with external systems
- **LLM**: Large Language Model, the foundation of agent intelligence
- **Context Window**: The amount of text an LLM can consider at once
- **Context Management**: Techniques to summarize and maintain relevant information within the context window
- **Tool Registry**: A service that manages available tools and their schemas
