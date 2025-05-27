<div align="center">

# 🏺👁️‍🗨️ Nubian AI Autonomous Agent Framework 👁️‍🗨️🏺

**Java-based Autonomous AI Assistant SDK**  
*Open-source generalist agent for real-world tasks via natural language and secure*

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

[![MIT License](https://img.shields.io/badge/License-MIT-green.svg)](https://choosealicense.com/licenses/mit/)
[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.0+-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Docker](https://img.shields.io/badge/Docker-Ready-blue.svg)](https://www.docker.com/)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](http://makeapullrequest.com)

[🚀 Quick Start](#-quick-start) • [📖 Documentation](#-documentation) • [🎯 Examples](#-examples) • [💬 Community](#-community)

![Nubian AI Demo](https://via.placeholder.com/800x400/1a1a1a/ffffff?text=Nubian+AI+Demo+Video)

</div>

## ✨ What is Nubian AI?

Nubian AI is an **intelligent agent that acts on your behalf** to accomplish complex tasks through natural conversation. Unlike traditional chatbots, Nubian can:

- 🌐 **Browse the web** and extract real-time information
- 📁 **Manage files** and create documents
- 💻 **Execute code** safely in isolated environments  
- ⚖️ **Handle legal workflows** with specialized tools
- 🔄 **Stream responses** in real-time

> **Perfect for:** Researchers, legal professionals, developers, and anyone who needs an AI assistant that can actually *do* things, not just talk about them.

## 🎯 Key Features

<table>
<tr>
<td width="50%">

### 🛡️ **Secure Sandbox Execution**
- Isolated Docker environments
- Safe code execution
- File system access
- Browser automation

</td>
<td width="50%">

### ⚡ **Real-time Streaming**
- Server-Sent Events (SSE)
- Redis pub/sub messaging
- Live task progress updates
- Responsive interactions

</td>
</tr>
<tr>
<td>

### 🔧 **Powerful Tool Integration**
- Web search & crawling
- Document processing
- API integrations
- Command-line execution

</td>
<td>

### ⚖️ **Legal AI Specialization**
- Case law research
- Contract drafting
- Meeting transcription
- Compliance automation

</td>
</tr>
</table>

## 🚀 Quick Start

Get Nubian AI running in under 5 minutes:

