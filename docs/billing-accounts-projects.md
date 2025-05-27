# Billing, Accounts, Projects, and Subscriptions

This document provides a detailed technical overview of the account system, project structure, billing architecture, and subscription management in the Nubian.so Java Agent system.

## 1. Account System Architecture

### 1.1 Core Entities and Relationships

The account system is structured around several key entities with hierarchical relationships:

```
┌───────────────────┐     ┌─────────────────┐     ┌────────────────────┐
│                   │     │                 │     │                    │
│  User             │─────▶  Account        │─────▶  Subscription      │
│                   │     │                 │     │                    │
└───────────────────┘     └─────────────────┘     └────────────────────┘
                                  │                         │
                                  ▼                         ▼
┌───────────────────┐     ┌─────────────────┐     ┌────────────────────┐
│                   │     │                 │     │                    │
│  Project          │◀────│  Organization   │─────▶  Billing Record    │
│                   │     │                 │     │                    │
└───────────────────┘     └─────────────────┘     └────────────────────┘
        │
        ▼
┌───────────────────┐     ┌─────────────────┐
│                   │     │                 │
│  Thread           │─────▶  Agent Run      │
│                   │     │                 │
└───────────────────┘     └─────────────────┘
```

#### Entity Definitions

1. **User**
   - Represents an individual user of the system
   - Contains authentication information (email, password hash)
   - Has a one-to-many relationship with Accounts
   - Stores personal preferences and settings

2. **Account**
   - The primary entity for billing and access control
   - Links to one or more Users with different permission levels
   - Contains contact information and account-wide settings
   - Has a one-to-one relationship with a Subscription

3. **Organization**
   - A grouping of users for team collaboration
   - Contains organization-specific settings and preferences
   - Has a many-to-many relationship with Users (through memberships)
   - Linked to multiple Projects

4. **Project**
   - A container for agent-related resources
   - Belongs to an Account or Organization
   - Contains configuration, API keys, and environment variables
   - Linked to one Sandbox environment

5. **Thread**
   - A conversation with an agent
   - Belongs to a Project
   - Contains a sequence of messages and tool calls
   - Has metadata about the conversation context

6. **Agent Run**
   - A specific execution of an agent
   - Belongs to a Thread
   - Contains execution details, status, and results
   - Used for billing and usage tracking

### 1.2 Database Schema

The database uses a relational schema with row-level security (RLS) policies for data isolation:

**users**
- id (PK, UUID)
- email (unique)
- password_hash
- created_at
- updated_at
- last_login_at
- status (active, suspended, etc.)

**accounts**
- id (PK, UUID)
- name
- created_at
- updated_at
- stripe_customer_id
- status
- plan_id
- settings (JSONB)

**organizations**
- id (PK, UUID)
- name
- account_id (FK)
- created_at
- updated_at
- settings (JSONB)

**projects**
- id (PK, UUID)
- name
- organization_id (FK)
- account_id (FK)
- created_at
- updated_at
- sandbox (JSONB with sandbox_id, sandbox_pass)
- api_key
- settings (JSONB)

**threads**
- id (PK, UUID)
- project_id (FK)
- title
- created_at
- updated_at
- metadata (JSONB)

**agent_runs**
- id (PK, UUID)
- thread_id (FK)
- status (pending, running, completed, failed)
- created_at
- updated_at
- completed_at
- error_message
- final_response_json (JSONB)

**messages**
- id (PK, UUID)
- thread_id (FK)
- role (user, assistant, tool)
- content (text)
- created_at
- tool_call_id (optional)
- is_summary (boolean)

### 1.3 Row-Level Security (RLS)

The system uses Postgres RLS policies to enforce access control at the database level:

- **User-level policies**: Restrict access to user's own data
- **Account-level policies**: Allow access to data belonging to user's account
- **Organization-level policies**: Allow access based on organization membership
- **Project-level policies**: Control access to project resources based on permissions

Sample RLS policy concept (without specific code):
- Users can only see projects in accounts they belong to
- Only account owners can modify billing information
- Thread access is restricted to users with project access

## 2. Project Management Architecture

### 2.1 Project Lifecycle

Projects follow a defined lifecycle:

1. **Creation**
   - Created by account or organization admin
   - Provisioned with default settings
   - Assigned API keys for authentication

2. **Configuration**
   - Environment variables set
   - Tool permissions configured
   - Access control defined

3. **Active Phase**
   - Threads and agent runs created
   - Resources consumed and tracked for billing
   - Performance metrics collected

4. **Archival/Deletion**
   - Projects can be archived (read-only state)
   - Deletion follows a soft-delete pattern with grace period
   - Resources released after confirmation

### 2.2 Project Resource Management

Projects serve as containers for various resources:

- **Sandbox Environment**: Isolated execution environment for agent code
- **API Keys**: Authentication tokens for external access
- **Environment Variables**: Configuration for tools and integrations
- **Storage Quota**: Allocated space for files and assets
- **Usage Limits**: Constraints based on subscription plan

### 2.3 Project Service Architecture

The `ProjectService` coordinates all project-related operations:

- **Project CRUD Operations**: Create, read, update, delete projects
- **Sandbox Provisioning**: Work with `SandboxService` to create environments
- **Permission Management**: Control access to project resources
- **Usage Tracking**: Monitor resource consumption for billing
- **Cross-Service Coordination**: Interface with other services like `ThreadManager`

## 3. Subscription and Billing Architecture

### 3.1 Subscription Models

The system supports multiple subscription tiers:

1. **Free Tier**
   - Limited agent runs per month
   - Basic model access only
   - Restricted tool set
   - Public sandboxes only

2. **Professional Tier**
   - Increased agent runs
   - Access to more powerful models
   - Extended tool set
   - Private sandboxes
   - Priority support

3. **Enterprise Tier**
   - Unlimited agent runs
   - Full model access including specialized models
   - Complete tool set with custom tools
   - Dedicated sandboxes
   - Premium support and SLAs

### 3.2 Billing Cycles and Metering

Billing operates on a combined subscription + usage model:

1. **Base Subscription**
   - Fixed monthly/annual fee
   - Billed at the start of each cycle
   - Provides resource quotas and feature access

2. **Usage-Based Billing**
   - Metered based on actual consumption
   - Key metrics include:
     - LLM token usage per model
     - Agent run duration
     - Storage utilization
     - Specialized tool usage
   - Billed at the end of each cycle

3. **Overages**
   - Usage beyond quota at premium rates
   - Option for hard limits to prevent unexpected costs
   - Real-time usage dashboard for monitoring

### 3.3 Stripe Integration Architecture

The billing system integrates with Stripe for payment processing:

1. **Customer Management**
   - Stripe customers mapped to Accounts
   - Payment methods stored in Stripe
   - Contact information synced bidirectionally

2. **Subscription Management**
   - Subscription creation and modification
   - Plan changes and prorations
   - Scheduled subscription updates

3. **Usage Reporting**
   - Metered usage reported via Stripe's Usage Record API
   - Subscription items for different usage categories
   - Usage aggregation for billing periods

4. **Invoice Generation**
   - Automatic invoice creation
   - PDF generation and email delivery
   - Payment collection and retry logic

5. **Webhook Processing**
   - Event-driven updates from Stripe
   - Handling payment successes and failures
   - Subscription lifecycle events

### 3.4 Billing Service Architecture

The `StripeBillingService` implements the `BillingService` interface:

- **Customer Operations**: Create, retrieve, update Stripe customers
- **Subscription Operations**: Manage subscription lifecycle
- **Usage Recording**: Track and report metered usage
- **Invoice Operations**: Generate and retrieve invoices
- **Portal Sessions**: Create Stripe Customer Portal sessions
- **Checkout Sessions**: Create Stripe Checkout sessions for new subscriptions

## 4. Usage Tracking and Cost Management

### 4.1 Usage Tracking Architecture

Usage is tracked at multiple levels for accurate billing:

1. **LLM Usage Tracking**
   - Token counts for each model
   - Prompt vs. completion tokens
   - Input/output ratio monitoring
   - Cost calculation based on model-specific rates

2. **Resource Utilization Tracking**
   - Sandbox runtime and resources
   - Storage consumption
   - API call volume
   - Background process usage

3. **Tool Usage Tracking**
   - Calls to premium tools
   - External API consumption
   - Specialized service usage
   - Data processing volume

### 4.2 Cost Allocation System

Costs are allocated using a hierarchical model:

1. **Run-Level Costs**
   - Direct costs of a specific agent run
   - LLM tokens, tool calls, runtime

2. **Project-Level Costs**
   - Aggregated costs across all runs in a project
   - Shared resources like storage and sandboxes

3. **Account-Level Costs**
   - Total costs across all projects
   - Account-wide resources and services
   - Subscription base fees

### 4.3 Usage Data Aggregation

Usage data flows through several stages:

1. **Real-time Collection**
   - Direct instrumentation in services
   - Event-based logging of usage metrics
   - Temporary storage in Redis

2. **Periodic Aggregation**
   - Background jobs to process usage events
   - Aggregation by time period and resource type
   - Storage in structured format for analysis

3. **Reporting and Visualization**
   - Dashboards for usage monitoring
   - Trend analysis and forecasting
   - Cost optimization recommendations

## 5. Authentication and Access Control

### 5.1 Authentication Architecture

The system uses a multi-layered authentication approach:

1. **User Authentication**
   - Email/password with secure hashing
   - OAuth integration for SSO
   - MFA for enhanced security

2. **Service Authentication**
   - API keys for project-level access
   - Service accounts for internal components
   - JWT-based token exchange

3. **Sandbox Authentication**
   - Isolated credentials per sandbox
   - Temporary access tokens
   - Secure credential storage

### 5.2 Authorization Model

Authorization follows a role-based access control (RBAC) model:

1. **Global Roles**
   - System Administrator
   - Billing Administrator
   - Support Agent

2. **Account Roles**
   - Account Owner
   - Account Administrator
   - Account Member

3. **Project Roles**
   - Project Administrator
   - Project Developer
   - Project Viewer

4. **Permission Categories**
   - Billing Management
   - User Management
   - Project Configuration
   - Agent Interaction
   - Data Access

### 5.3 Permission Enforcement

Permissions are enforced at multiple levels:

1. **API Gateway**
   - Authentication validation
   - Basic permission checks
   - Rate limiting and throttling

2. **Service Layer**
   - Detailed permission verification
   - Context-specific access control
   - Data filtering based on permissions

3. **Database Layer**
   - Row-level security policies
   - Attribute-based access control
   - Audit logging of sensitive operations

## 6. Account and User Management

### 6.1 User Lifecycle

Users follow a defined lifecycle:

1. **Registration**
   - Email verification
   - Profile creation
   - Initial role assignment

2. **Account Association**
   - Joining existing accounts
   - Creating new accounts
   - Managing multiple account memberships

3. **Profile Management**
   - Contact information updates
   - Password changes
   - Notification preferences

4. **Deactivation/Deletion**
   - Account disassociation
   - Data export and portability
   - Privacy compliance (GDPR, CCPA)

### 6.2 Account Hierarchy

Accounts can have complex organizational structures:

1. **Personal Accounts**
   - Single user as owner
   - Simplified billing and management
   - Limited sharing capabilities

2. **Team Accounts**
   - Multiple users with defined roles
   - Shared projects and resources
   - Collaborative workflows

3. **Enterprise Accounts**
   - Multiple organizations
   - Hierarchical team structures
   - Centralized billing with departmental allocations
   - Custom integration and security requirements

### 6.3 Account Service Architecture

The `AccountService` manages all account-related operations:

- **Account CRUD Operations**: Create, read, update, delete accounts
- **User Association**: Add/remove users from accounts
- **Role Management**: Assign and modify user roles
- **Organization Structure**: Manage teams and departments
- **Settings Management**: Configure account-wide settings

## 7. Database Interaction Patterns

### 7.1 Data Access Layer

The system uses a structured data access approach:

1. **Repository Pattern**
   - Entity-specific repositories
   - Basic CRUD operations
   - Query optimization and caching

2. **Service Layer**
   - Business logic implementation
   - Transaction management
   - Cross-entity operations

3. **DTO Transformation**
   - Conversion between entities and DTOs
   - Response shaping based on context
   - Selective data loading for performance

### 7.2 Transaction Management

Transactions are managed to ensure data consistency:

1. **ACID Compliance**
   - Atomic operations for related changes
   - Consistent state transitions
   - Isolated execution contexts
   - Durable storage with proper recovery

2. **Distributed Transactions**
   - Saga pattern for cross-service operations
   - Compensation actions for failures
   - Event-based consistency for eventual consistency

3. **Optimistic Concurrency**
   - Version-based conflict detection
   - Retry logic for concurrent modifications
   - User-friendly conflict resolution

### 7.3 Migration and Schema Evolution

The database schema evolves through controlled processes:

1. **Migration Scripts**
   - Versioned schema changes
   - Forward and backward compatibility
   - Data transformation during migration

2. **Schema Validation**
   - Runtime checks for expected schema
   - Graceful handling of schema mismatches
   - Automated testing of migrations

3. **Zero-Downtime Updates**
   - Compatible intermediate states
   - Phased rollout of schema changes
   - Feature flags for new schema capabilities

## 8. Integration Points

### 8.1 External Service Integration

The system integrates with several external services:

1. **Stripe**
   - Payment processing
   - Subscription management
   - Invoice generation

2. **Daytona**
   - Sandbox provisioning
   - Environment management
   - Resource allocation

3. **LLM Providers**
   - OpenAI
   - Anthropic
   - Others as configured

4. **Storage Services**
   - S3 for persistent storage
   - CDN for public assets
   - Temporary storage for processing

### 8.2 Internal Service Communication

Services communicate through defined patterns:

1. **Synchronous Communication**
   - REST APIs for direct requests
   - gRPC for high-performance internal calls
   - GraphQL for flexible data fetching

2. **Asynchronous Communication**
   - Redis Pub/Sub for real-time events
   - Message queues for background processing
   - Event streams for audit and analytics

3. **Service Discovery**
   - Registry-based service location
   - Load balancing and failover
   - Circuit breaking for resilience

## 9. Security and Compliance

### 9.1 Data Protection

User and account data is protected through multiple mechanisms:

1. **Encryption**
   - Data at rest encryption
   - Transport layer security (TLS)
   - End-to-end encryption for sensitive data

2. **Data Minimization**
   - Purpose-specific data collection
   - Retention policies and automatic purging
   - Anonymization for analytics

3. **Access Controls**
   - Principle of least privilege
   - Just-in-time access provisioning
   - Audit logging for sensitive operations

### 9.2 Compliance Framework

The system is designed to support regulatory compliance:

1. **Privacy Regulations**
   - GDPR compliance mechanisms
   - CCPA data rights implementation
   - Privacy by design principles

2. **Industry Standards**
   - SOC 2 control implementation
   - HIPAA compatibility (where applicable)
   - PCI DSS for payment processing

3. **Audit and Reporting**
   - Comprehensive audit trails
   - Automated compliance reporting
   - Regular security assessments
