# executeByToolNameAsync Code Flow

## Overview
This document describes the simplified code flow for `executeByToolNameAsync` endpoint, linking functions with simple names and arguments without exposing execution details.

## Flow Diagram

### Main Flow

```
Frontend Request
  └─> POST /api/executor/executeByToolNameAsync
      └─> executeByToolNameAsync(request)
          ├─> validateRequest(toolName)
          ├─> isVue(request) → boolean
          ├─> getPlanTemplateIdFromTool(toolName) → planTemplateId
          ├─> validateOrGenerateConversationId(conversationId) → conversationId
          ├─> extractRequestParams(request) → {uploadedFiles, uploadKey, replacementParams}
          ├─> saveMemory(conversationId, query) → Memory
          ├─> executePlanTemplate(planTemplateId, uploadedFiles, conversationId, replacementParams, isVueRequest, uploadKey) → PlanExecutionWrapper
          │   ├─> generatePlanId() → {currentPlanId, rootPlanId}
          │   ├─> getLatestPlanVersion(planTemplateId) → planJson
          │   ├─> replaceParameters(planJson, replacementParams) → planJson
          │   ├─> parsePlan(planJson) → PlanInterface
          │   ├─> attachUploadedFiles(plan, uploadedFiles) → PlanInterface
          │   └─> planningCoordinator.executeByPlan(plan, rootPlanId, parentPlanId, currentPlanId, toolcallId, isVueRequest, uploadKey, depth, conversationId) → CompletableFuture<PlanExecutionResult>
          │       ├─> createExecutionContext(plan, rootPlanId, parentPlanId, currentPlanId, toolcallId, isVueRequest, uploadKey, depth, conversationId) → ExecutionContext
          │       ├─> planExecutorFactory.createExecutor(plan) → PlanExecutorInterface
          │       ├─> executor.executeAllStepsAsync(context) → CompletableFuture<PlanExecutionResult>
          │       │   ├─> getExecutorForLevel(planDepth) → ExecutorService
          │       │   ├─> syncUploadedFilesToPlan(context) → void
          │       │   ├─> recordPlanExecutionStart(context) → void
          │       │   ├─> executeStep(step, context) → BaseAgent (for each step)
          │       │   │   └─> agent.step() → AgentExecResult
          │       │   ├─> collectStepResult(step, executor) → StepResult
          │       │   ├─> checkInterruption(rootPlanId) → boolean
          │       │   ├─> checkStepFailure(step) → boolean
          │       │   └─> performCleanup(context, lastExecutor) → void
          │       └─> planFinalizer.handlePostExecution(context, result) → PlanExecutionResult
          │           ├─> isTaskInterrupted(context, result) → boolean
          │           ├─> generateSummary(context, result) → void (if needSummary)
          │           ├─> generateDirectResponse(context, result) → void (if directResponse)
          │           └─> processAndRecordResult(context, result, finalResult) → void
          ├─> createOrUpdateTask(rootPlanId, START) → TaskEntity
          ├─> registerCompletionHandler(wrapper, rootPlanId) → void
          └─> buildResponse(wrapper, conversationId, toolName, planTemplateId) → Response
```

## Function Breakdown

### 1. Request Validation & Preparation

**Function:** `executeByToolNameAsync(request: Map<String, Object>)`

**Steps:**
1. `validateRequest(toolName: String)` - Validates toolName is not empty
2. `isVue(request: Map)` - Determines if request is from Vue frontend
3. `getPlanTemplateIdFromTool(toolName: String)` - Resolves planTemplateId from tool name
4. `validateOrGenerateConversationId(conversationId: String?)` - Validates or generates conversation ID

**Key Functions:**
- `isVue(request)` - Checks request source (Vue vs HTTP)
- `getPlanTemplateIdFromTool(toolName)` - Maps tool name to plan template ID
- `validateOrGenerateConversationId(conversationId)` - Ensures conversation ID exists

### 2. Request Parameter Extraction

**Function:** `extractRequestParams(request: Map<String, Object>)`

**Returns:**
- `uploadedFiles: List<String>?` - List of uploaded file names
- `uploadKey: String?` - Upload key for file context
- `replacementParams: Map<String, Object>?` - Parameters for template replacement

### 3. Memory Management

**Function:** `saveMemory(conversationId: String, query: String)`

**Flow:**
```
saveMemory(conversationId, query)
  └─> memoryService.saveMemory(Memory(conversationId, query))
      └─> MemoryRepository.save(MemoryEntity)
```

### 4. Plan Template Execution

**Function:** `executePlanTemplate(planTemplateId, uploadedFiles, conversationId, replacementParams, isVueRequest, uploadKey)`

**Returns:** `PlanExecutionWrapper(future: CompletableFuture<PlanExecutionResult>, rootPlanId: String)`

**Internal Steps:**
1. `generatePlanId()` → `{currentPlanId, rootPlanId}`
2. `getLatestPlanVersion(planTemplateId)` → `planJson: String`
3. `replaceParameters(planJson, replacementParams)` → `planJson: String`
4. `parsePlan(planJson)` → `PlanInterface`
5. `attachUploadedFiles(plan, uploadedFiles)` → `PlanInterface`
6. `planningCoordinator.executeByPlan(...)` → `CompletableFuture<PlanExecutionResult>`

**Key Functions:**
- `generatePlanId()` - Creates unique plan ID
- `getLatestPlanVersion(planTemplateId)` - Retrieves latest plan template JSON
- `replaceParameters(planJson, params)` - Replaces `<<param>>` placeholders
- `parsePlan(planJson)` - Converts JSON to PlanInterface
- `attachUploadedFiles(plan, files)` - Attaches file info to plan steps
- `planningCoordinator.executeByPlan(...)` - Starts plan execution

### 4.1. PlanningCoordinator.executeByPlan() Detailed Flow

**Function:** `planningCoordinator.executeByPlan(plan, rootPlanId, parentPlanId, currentPlanId, toolcallId, isVueRequest, uploadKey, depth, conversationId)`

**Returns:** `CompletableFuture<PlanExecutionResult>`

**Internal Steps:**

1. **Create Execution Context**
   - `createExecutionContext(...)` → `ExecutionContext`
   - Sets userRequest, planIds, planDepth, conversationId, uploadKey
   - Determines if summary is needed (toolcallId == null && isVueRequest)

2. **Create Executor**
   - `planExecutorFactory.createExecutor(plan)` → `PlanExecutorInterface`
   - Factory selects executor based on plan type (e.g., "dynamic_agent")

3. **Execute All Steps**
   - `executor.executeAllStepsAsync(context)` → `CompletableFuture<PlanExecutionResult>`
   - Gets executor pool for plan depth
   - Syncs uploaded files to plan directory
   - Records plan execution start
   - For each step:
     - Checks for interruption
     - Executes step via `executeStep(step, context)` → `BaseAgent`
     - Collects step result
     - Checks for step failure or interruption
   - Performs cleanup after execution

4. **Post-Execution Processing**
   - `planFinalizer.handlePostExecution(context, result)` → `PlanExecutionResult`
   - Checks if task was interrupted
   - Generates summary if needed (via LLM)
   - Generates direct response if plan is directResponse type
   - Processes and records final result

**Key Functions:**
- `createExecutionContext(...)` - Builds execution context with all parameters
- `planExecutorFactory.createExecutor(plan)` - Creates appropriate executor for plan type
- `executor.executeAllStepsAsync(context)` - Executes all plan steps asynchronously
- `executeStep(step, context)` - Executes individual step using agent
- `checkInterruption(rootPlanId)` - Checks if execution should be stopped
- `planFinalizer.handlePostExecution(context, result)` - Post-processing (summary, recording)

### 5. Task Management

**Function:** `createOrUpdateTask(rootPlanId: String, state: START)`

**Flow:**
```
createOrUpdateTask(rootPlanId, START)
  └─> rootTaskManagerService.createOrUpdateTask(rootPlanId, START)
      └─> TaskRepository.save(TaskEntity)
```

### 6. Async Completion Handling

**Function:** `registerCompletionHandler(wrapper: PlanExecutionWrapper, rootPlanId: String)`

**Flow:**
```
wrapper.getResult().whenComplete((result, throwable) -> {
  if (throwable != null)
    └─> updateTaskResult(rootPlanId, "Execution failed: " + error)
  else
    └─> updateTaskResult(rootPlanId, result.getFinalResult())
})
```

**Key Functions:**
- `updateTaskResult(rootPlanId, result)` - Updates task result in database

### 7. Response Building

**Function:** `buildResponse(wrapper, conversationId, toolName, planTemplateId)`

**Returns:**
```json
{
  "planId: wrapper.getRootPlanId(),
  "status": "processing",
  "message": "Task submitted, processing",
  "conversationId": conversationId,
  "toolName": toolName,
  "planTemplateId": planTemplateId
}
```

## Error Handling

**Function:** `handleError(exception: Exception, toolName: String, planTemplateId: String)`

**Returns:**
```json
{
  "error": "Failed to start plan execution: " + message,
  "toolName": toolName,
  "planTemplateId": planTemplateId
}
```

## Key Components

### Request Parameters
- `toolName: String` - Tool name or plan template ID
- `conversationId: String?` - Optional conversation ID
- `uploadedFiles: List<String>?` - Optional uploaded files
- `uploadKey: String?` - Optional upload key
- `replacementParams: Map<String, Object>?` - Optional replacement parameters
- `isVueRequest: Boolean?` - Optional flag for Vue frontend

### Response Structure
- `planId: String` - Root plan ID for tracking
- `status: String` - Task status ("processing")
- `message: String` - Status message
- `conversationId: String` - Conversation ID (validated/generated)
- `toolName: String` - Original tool name
- `planTemplateId: String` - Resolved plan template ID

### Internal Data Flow

```
Request
  → Validate & Prepare
    → Extract Parameters
      → Save Memory
        → Execute Plan Template
          → Generate Plan ID
          → Load Plan Template
          → Replace Parameters
          → Parse Plan
          → Attach Files
          → PlanningCoordinator.executeByPlan()
            → Create Execution Context
            → Create Executor (Factory Pattern)
            → Execute All Steps (Async)
              → Sync Uploaded Files
              → Record Execution Start
              → For Each Step:
                → Check Interruption
                → Execute Step (Agent)
                → Collect Step Result
                → Check Failure
              → Perform Cleanup
            → Post-Execution Processing
              → Check Interruption
              → Generate Summary (if needed)
              → Generate Direct Response (if needed)
              → Record Final Result
        → Create Task
        → Register Completion Handler
      → Build Response
  → Return Response
```

## Summary

**Main Flow:**
1. **Validate** request and resolve plan template ID
2. **Prepare** conversation ID and extract parameters
3. **Save** memory for conversation tracking
4. **Execute** plan template asynchronously
5. **Register** task and completion handler
6. **Return** response with plan ID and status

**Key Characteristics:**
- Asynchronous execution (fire-and-forget)
- Task tracking via rootTaskManagerService
- Automatic conversation ID generation if not provided
- Parameter replacement support for plan templates
- File upload support with automatic attachment to plan steps
- Step-by-step execution with interruption support
- Post-execution processing (summary generation, result recording)
- Factory pattern for executor selection based on plan type

