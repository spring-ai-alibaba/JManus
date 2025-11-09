# Memory Management Flow (Frontend to Backend)

## Overview
This document describes the complete memory management flow from frontend to backend, including how memories are created and updated.

## Flow Diagram

### 1. Memory Creation Flow

```
Frontend (Vue3)
  └─> MemoryApiService.createMemory()
      └─> POST /api/memories
          └─> MemoryController.createMemory()
              └─> MemoryService.saveMemory()
                  └─> MemoryRepository.save()
                      └─> MemoryEntity (saved to database)
```

**Key Functions:**
- `MemoryApiService.createMemory()` - Frontend API call
- `MemoryController.createMemory()` - REST endpoint handler
- `MemoryService.saveMemory()` - Business logic (create or update)
- `MemoryRepository.save()` - Database persistence

### 2. Memory Update Flow

```
Frontend (Vue3)
  └─> MemoryApiService.updateMemory()
      └─> PUT /api/memories
          └─> MemoryController.updateMemory()
              └─> MemoryService.updateMemory()
                  └─> MemoryRepository.save()
                      └─> MemoryEntity (updated in database)
```

**Key Functions:**
- `MemoryApiService.updateMemory()` - Frontend API call
- `MemoryController.updateMemory()` - REST endpoint handler
- `MemoryService.updateMemory()` - Business logic (update only)
- `MemoryRepository.save()` - Database persistence

### 3. Chat Memory Flow (During Conversation)

#### 3.1 User Message Flow

```
Frontend
  └─> User sends message
      └─> Chat request with conversationId
          └─> CustomMessageChatMemoryAdvisor.before()
              ├─> chatMemory.get(conversationId) - Retrieve history
              ├─> Merge history with new message
              └─> chatMemory.add(conversationId, userMessage) - Save user message
                  └─> ChatMemoryRepository.save() - Persist to database
```

**Key Functions:**
- `CustomMessageChatMemoryAdvisor.before()` - Advisor hook before LLM call
- `chatMemory.get()` - Retrieve conversation history
- `chatMemory.add()` - Add new message to memory
- `ChatMemoryRepository.save()` - Persist to database (ai_chat_memory table)

#### 3.2 Assistant Response Flow

```
LLM Response
  └─> CustomMessageChatMemoryAdvisor.after()
      └─> chatMemory.add(conversationId, assistantMessages) - Save assistant response
          └─> ChatMemoryRepository.save() - Persist to database
```

**Key Functions:**
- `CustomMessageChatMemoryAdvisor.after()` - Advisor hook after LLM call
- `chatMemory.add()` - Add assistant response to memory
- `ChatMemoryRepository.save()` - Persist to database

### 4. Agent Memory Flow (Internal Agent Operations)

```
DynamicAgent.think()
  └─> getAgentMemory().get(planId) - Retrieve agent memory
      └─> Build prompt with history
          └─> LLM call
              └─> DynamicAgent.processMemory()
                  ├─> getAgentMemory().clear(planId) - Clear old memory
                  └─> getAgentMemory().add(planId, message) - Add new messages
                      └─> InMemoryChatMemoryRepository (in-memory only)
```

**Key Functions:**
- `getAgentMemory().get()` - Retrieve agent conversation history
- `getAgentMemory().clear()` - Clear current plan memory
- `getAgentMemory().add()` - Add messages to agent memory
- Note: Agent memory uses `InMemoryChatMemoryRepository` (not persisted to DB)

### 5. Memory Retrieval Flow

```
Frontend
  └─> MemoryApiService.getMemory(conversationId)
      └─> GET /api/memories/single?conversationId=xxx
          └─> MemoryController.singleMemory()
              └─> MemoryService.singleMemory()
                  └─> MemoryRepository.findByConversationId()
                      └─> Returns MemoryEntity with messages
```

**Key Functions:**
- `MemoryApiService.getMemory()` - Frontend API call
- `MemoryController.singleMemory()` - REST endpoint handler
- `MemoryService.singleMemory()` - Business logic
- `MemoryRepository.findByConversationId()` - Database query

## Key Components

### Frontend Layer
- `MemoryApiService` - API service for memory operations
  - `createMemory()` - Create new memory
  - `updateMemory()` - Update existing memory
  - `getMemory()` - Retrieve memory by conversationId
  - `getMemories()` - Get all memories
  - `deleteMemory()` - Delete memory

### Backend Controller Layer
- `MemoryController` - REST API endpoints
  - `createMemory()` - POST /api/memories
  - `updateMemory()` - PUT /api/memories
  - `singleMemory()` - GET /api/memories/single
  - `getAllMemories()` - GET /api/memories
  - `deleteMemory()` - DELETE /api/memories/{conversationId}

### Backend Service Layer
- `MemoryService` - Business logic
  - `saveMemory()` - Create or update memory (upsert)
  - `updateMemory()` - Update existing memory only
  - `singleMemory()` - Get memory by conversationId
  - `getMemories()` - Get all memories
  - `deleteMemory()` - Delete memory and clear chat memory
  - `generateConversationId()` - Generate unique conversation ID

### Memory Advisors
- `CustomMessageChatMemoryAdvisor` - Manages chat memory during conversations
  - `before()` - Load history and save user message
  - `after()` - Save assistant response
  - Uses `ChatMemory` interface for operations

### Memory Repositories
- `MemoryRepository` - JPA repository for MemoryEntity
  - `save()` - Save/update memory metadata
  - `findByConversationId()` - Find by conversation ID
  - `deleteByConversationId()` - Delete by conversation ID

- `ChatMemoryRepository` - Spring AI chat memory repository
  - `save()` - Save message to ai_chat_memory table
  - `get()` - Retrieve messages by conversationId
  - `deleteByConversationId()` - Clear messages for conversation

### Memory Types

1. **Conversation Memory** (`ChatMemory`)
   - Stored in database (`ai_chat_memory` table)
   - Managed by `CustomMessageChatMemoryAdvisor`
   - Persisted via `JdbcChatMemoryRepository` (H2/MySQL/PostgreSQL)

2. **Agent Memory** (`ChatMemory`)
   - Stored in-memory only (`InMemoryChatMemoryRepository`)
   - Managed by `DynamicAgent` and `BaseAgent`
   - Scoped by `planId` (not conversationId)
   - Cleared and rebuilt during agent execution

## Database Schema

### dynamic_memories Table
- Stores memory metadata (conversationId, memoryName, createTime)
- One-to-many relationship with `conversation_messages`

### conversation_messages Table
- Stores individual messages (messageType, content, metadata)
- Linked to `dynamic_memories` via foreign key

### ai_chat_memory Table
- Stores chat history for Spring AI ChatMemory
- Used by `JdbcChatMemoryRepository`
- Fields: conversation_id, content, type, timestamp

## Summary

**Memory Creation:**
1. Frontend calls `createMemory()` API
2. Backend saves `MemoryEntity` to database
3. Conversation starts with empty chat memory

**Memory Update:**
1. Frontend calls `updateMemory()` API
2. Backend updates `MemoryEntity` in database
3. Only metadata (memoryName) is updated, messages remain unchanged

**Chat Memory During Conversation:**
1. User message → `CustomMessageChatMemoryAdvisor.before()` → Save to `ChatMemory`
2. LLM response → `CustomMessageChatMemoryAdvisor.after()` → Save to `ChatMemory`
3. All messages persisted to `ai_chat_memory` table via `ChatMemoryRepository`

**Agent Memory (Internal):**
1. Agent retrieves history via `getAgentMemory().get(planId)`
2. After tool execution, agent clears and rebuilds memory via `processMemory()`
3. Agent memory is in-memory only, not persisted

