# Call Tree: Send Query Flow

## Overview
This document shows the complete call tree when a user clicks the "Send Query" button.

---

## Frontend Flow

### 1. User Interaction
```
InputArea.vue
└── handleSend() [Line 447]
    ├── Validates input (trim, disabled check)
    ├── saveToHistory(finalInput)
    ├── Prepares query object:
    │   ├── input: finalInput
    │   ├── memoryId: memoryStore.selectMemoryId
    │   ├── uploadedFiles: uploadedFiles.value
    │   └── uploadKey: uploadKey.value (if exists)
    ├── If tool selected:
    │   ├── Adds toolName: selectedTool.planTemplateId
    │   └── Adds replacementParams: { [paramName]: finalInput }
    └── Calls: messageDialog.sendMessage(query)
```

### 2. Message Dialog Handler
```
useMessageDialog.ts
└── sendMessage(query, dialogId?) [Line 174]
    ├── Sets loading state: isLoading.value = true
    ├── Gets or creates active dialog
    ├── Adds user message to dialog
    ├── Adds assistant "Processing..." message
    ├── Determines execution path:
    │   ├── If toolName && replacementParams:
    │   │   └── DirectApiService.executeByToolName(...)
    │   └── Else:
    │       └── DirectApiService.sendMessageWithDefaultPlan(...)
    ├── Updates conversationId if present
    ├── If planId returned:
    │   ├── Updates message with plan execution info
    │   └── Starts polling: planExecution.handlePlanExecutionRequested(...)
    └── Returns: { success, planId?, conversationId? }
```

### 3. API Service Layer
```
direct-api-service.ts
├── executeByToolName(toolName, replacementParams?, uploadedFiles?, uploadKey?, requestSource)
│   [Line 67]
│   ├── Builds request body:
│   │   ├── toolName
│   │   ├── requestSource: 'VUE_DIALOG'
│   │   ├── conversationId (from memoryStore if available)
│   │   ├── replacementParams
│   │   ├── uploadedFiles
│   │   └── uploadKey
│   └── POST /api/executor/executeByToolNameAsync
│
└── sendMessageWithDefaultPlan(query, requestSource) [Line 44]
    ├── Sets toolName: 'default-plan-id-001000222'
    ├── Creates replacementParams: { userRequirement: query.input }
    └── Calls: executeByToolName(...)
```

---

## Backend Flow

### 4. Controller Entry Point
```
ManusController.java
└── executeByToolNameAsync(@RequestBody Map<String, Object> request) [Line 189]
    ├── Validates toolName
    ├── Gets requestSource: VUE_DIALOG
    ├── Resolves planTemplateId:
    │   ├── Tries: getPlanTemplateIdFromTool(toolName)
    │   └── Falls back: toolName (if tool not published)
    ├── Validates/generates conversationId
    ├── Extracts:
    │   ├── uploadedFiles
    │   ├── uploadKey
    │   └── replacementParams
    ├── Calls: executePlanTemplate(...) [Line 241]
    ├── Creates/updates RootTaskManagerEntity
    ├── Starts async execution (fire and forget)
    └── Returns: { planId, status: "processing", conversationId, ... }
```

### 5. Plan Template Execution
```
ManusController.java
└── executePlanTemplate(planTemplateId, uploadedFiles, conversationId, replacementParams, requestSource, uploadKey)
    [Line 535]
    ├── Generates planId: planIdDispatcher.generatePlanId()
    ├── Gets plan JSON: planTemplateService.getLatestPlanVersion(planTemplateId)
    ├── Parameter replacement:
    │   └── parameterMappingService.replaceParametersInJson(planJson, parametersForReplacement)
    ├── Parses plan JSON: objectMapper.readValue(planJson, PlanInterface.class)
    ├── Attaches uploaded files to step requirements
    ├── Creates/updates Memory (if conversationId exists)
    └── Calls: planningCoordinator.executeByPlan(...) [Line 621]
        └── Returns: PlanExecutionWrapper(future, rootPlanId)
```

### 6. Planning Coordinator
```
PlanningCoordinator.java
└── executeByPlan(plan, rootPlanId, parentPlanId, currentPlanId, toolcallId, requestSource, uploadKey, planDepth, conversationId)
    [Line 72]
    ├── Creates ExecutionContext:
    │   ├── Sets title, planId, rootPlanId, plan, planDepth
    │   ├── Sets needSummary: true (for VUE requests without toolcallId)
    │   ├── Sets conversationId (generates if VUE_DIALOG)
    │   ├── Sets uploadKey, parentPlanId, toolCallId
    │   └── Sets useConversation: true
    ├── Gets executor: planExecutorFactory.createExecutor(plan)
    ├── Executes: executor.executeAllStepsAsync(context)
    └── Post-processing: planFinalizer.handlePostExecution(context, result)
```

### 7. Plan Executor
```
PlanExecutorFactory
└── createExecutor(plan)
    └── Returns: PlanExecutorInterface (AbstractPlanExecutor implementation)

AbstractPlanExecutor (or implementation)
└── executeAllStepsAsync(context)
    ├── Iterates through plan steps
    ├── For each step:
    │   ├── Executes agent with step requirement
    │   ├── Handles tool calls (may trigger subplans)
    │   ├── Records execution: planExecutionRecorder.record(...)
    │   └── Updates step status
    ├── Handles user input waits (if needed)
    └── Returns: CompletableFuture<PlanExecutionResult>
```

---

## Frontend Polling Flow (After Initial Request)

### 8. Plan Execution Polling
```
usePlanExecution.ts
└── handlePlanExecutionRequested(planId) [Line 468]
    ├── Calls: initiatePlanExecutionSequence('Execute Plan', planId) [Line 419]
    │   ├── Marks task as running: taskStore.setTaskRunning(planId)
    │   ├── Emits dialog round start
    │   └── Starts polling: startPolling() [Line 380]
    │       └── Sets interval: pollTimer = setInterval(pollPlanStatus, POLL_INTERVAL)
    │
    └── pollPlanStatus() [Line 350]
        ├── Calls: getPlanDetails(activePlanId.value) [Line 229]
        │   └── GET /api/executor/details/{planId}
        │       └── ManusController.getExecutionDetails(planId) [Line 345]
        │           ├── Reads execution record: planHierarchyReaderService.readExecutionRecord(planId)
        │           └── Returns: PlanExecutionRecord
        ├── Checks plan status:
        │   ├── If completed: handlePlanCompletion(details)
        │   ├── If failed: handlePlanError(details)
        │   └── If running: Updates UI with latest status
        └── Emits events for UI updates
```

### 9. Status Updates
```
useMessageDialog.ts (via polling updates)
└── Updates assistant message with:
    ├── Current step information
    ├── Agent execution records
    ├── Tool call results
    ├── Final result (when completed)
    └── Error messages (if failed)
```

---

## Complete Call Sequence Diagram

```
User clicks "Send Query"
    │
    ▼
[InputArea.vue] handleSend()
    │
    ▼
[useMessageDialog.ts] sendMessage()
    │
    ▼
[direct-api-service.ts] executeByToolName() or sendMessageWithDefaultPlan()
    │
    │ HTTP POST /api/executor/executeByToolNameAsync
    │
    ▼
[ManusController.java] executeByToolNameAsync()
    │
    ▼
[ManusController.java] executePlanTemplate()
    │
    ▼
[PlanningCoordinator.java] executeByPlan()
    │
    ▼
[PlanExecutorFactory] createExecutor() → [AbstractPlanExecutor] executeAllStepsAsync()
    │
    │ (Async execution starts)
    │
    ▼
[ManusController.java] Returns { planId, status: "processing" }
    │
    ▼
[useMessageDialog.ts] Receives planId, starts polling
    │
    ▼
[usePlanExecution.ts] handlePlanExecutionRequested() → startPolling()
    │
    └──→ [Polling Loop] Every 5 seconds:
            │
            ├──→ GET /api/executor/details/{planId}
            │
            ├──→ [ManusController.java] getExecutionDetails()
            │
            ├──→ [usePlanExecution.ts] Updates UI with status
            │
            └──→ [useMessageDialog.ts] Updates message content
                    │
                    └──→ (Continues until plan completed/failed)
```

---

## Key Components Summary

### Frontend Components
- **InputArea.vue**: User input UI component
- **useMessageDialog.ts**: Message dialog management composable
- **direct-api-service.ts**: API service for backend communication
- **usePlanExecution.ts**: Plan execution polling and state management

### Backend Components
- **ManusController.java**: REST API controller
- **PlanningCoordinator.java**: Coordinates plan execution
- **PlanExecutorFactory**: Creates appropriate plan executor
- **AbstractPlanExecutor**: Base executor for plan steps
- **PlanTemplateService**: Manages plan templates
- **ParameterMappingService**: Handles parameter replacement
- **MemoryService**: Manages conversation memory
- **PlanExecutionRecorder**: Records execution history

---

## Data Flow

1. **Query Object**: `{ input, memoryId, uploadedFiles, uploadKey, toolName?, replacementParams? }`
2. **Request Body**: `{ toolName, requestSource, conversationId, replacementParams, uploadedFiles, uploadKey }`
3. **Response**: `{ planId, status, conversationId, toolName, planTemplateId }`
4. **Polling Response**: `PlanExecutionRecord { status, steps, agents, completed, ... }`

---

## Notes

- The execution is **asynchronous** - the backend returns immediately with a planId
- Frontend uses **polling** (every 5 seconds) to check execution status
- Plan execution may trigger **subplans** (nested plan execution)
- User input may be required during execution (wait states)
- Execution results are recorded in the database for later retrieval

