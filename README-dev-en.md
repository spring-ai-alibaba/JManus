# Spring AI Alibaba Lynxe Developer Quick Start

This project is Alibaba's AI plan execution and management system, built with Spring Boot. It supports plan generation, asynchronous execution, status tracking, and configuration management. Below is a guide for developers to quickly debug and understand the core entry points and interfaces.

## 1. Core Module API Entry Points

- All core REST APIs are located in `src/main/java/com/alibaba/cloud/ai/lynxe/runtime/controller/` and `config/` directories.
- Recommended starting points: `LynxeController.java`, `PlanTemplateController.java`, and `ConfigController.java` for the main business logic.

## 2. Prerequisites: Create and Publish Func-Agent

**Important**: Before using synchronous/asynchronous execution endpoints, you must complete the following steps:

1. **Create Func-Agent**
   - Create and configure a Func-Agent (plan template) in the system interface
   - Define plan steps, agent configuration, tool selection, etc.

2. **Publish as Tool**
   - In the Func-Agent configuration interface, click the "Publish Service" button
   - Select "Publish as Internal Method Call"
   - Set tool name (toolName), tool description, parameter definitions, etc.

3. **Publish as HTTP Service** (Optional but Recommended)
   - In the publish service interface, select "Publish as HTTP Service"
   - The system will generate corresponding HTTP API call examples

**Only after completing the above steps can you call the Func-Agent via `executeByToolNameAsync` or `executeByToolNameSync` endpoints.**

Frontend implementation reference:
- `ui-vue3/src/components/sidebar/ExecutionController.vue`: Execution controller with publish service button
- `ui-vue3/src/components/publish-service-modal/PublishServiceModal.vue`: Publish service modal

## 3. Task Initiation and Execution

### Asynchronous Execution (Recommended)

- **API File**: `LynxeController.java`
- **Main Endpoint**: `POST /api/executor/executeByToolNameAsync`
- **Request Parameters**:
  ```json
  {
    "toolName": "my-tool",
    "serviceGroup": "research",  // Required
    "replacementParams": {       // Optional, for replacing <<param>> placeholders in plan template
      "param1": "value1",
      "param2": "value2"
    },
    "uploadedFiles": ["file1.pdf", "file2.txt"],  // Optional, list of uploaded file names
    "uploadKey": "upload-1234567890_1234_1",      // Optional, unique identifier for file upload session
    "conversationId": "xxx"                       // Optional, conversation ID (for memory management)
  }
  ```

- **File Upload Parameters**:
  - `uploadedFiles`: String array, containing original file names of uploaded files
    - Obtained after uploading files via `POST /api/file-upload/upload` endpoint
    - Backend appends file names to each execution step's `stepRequirement` for LLM use
    - Example: `["report.pdf", "data.csv"]`
  
  - `uploadKey`: String, unique identifier for file upload session
    - Automatically generated and returned by backend when uploading files (format: `upload-{timestamp}_{random}_{threadId}`)
    - Used to identify files from the same upload batch and sync files to plan directory during execution
    - Backend uses `uploadKey` to call `syncUploadedFilesToPlan()` to copy files to plan working directory
    - Example: `"upload-1703123456789_5678_42"`
  
  - **Parameter Usage**:
    - **Recommended to use both** `uploadedFiles` and `uploadKey` together:
      - `uploadedFiles` lets LLM know which files are available (via stepRequirement)
      - `uploadKey` ensures files are synced to plan working directory, making them accessible to agents
      - Using both together provides the best experience
    - Can also be used separately:
      - Only `uploadedFiles`: LLM will know file names, but files may not be in plan directory
      - Only `uploadKey`: Files will be synced to plan directory, but LLM may not know which files are available
  
  - **File Upload Workflow**:
    1. Call `POST /api/file-upload/upload` to upload files (use FormData with field name: `files`)
    2. Backend returns `FileUploadResult` containing:
       - `uploadKey`: Upload session identifier
       - `uploadedFiles`: File information array (each contains `originalName`, `size`, `type`, etc.)
    3. When calling execution endpoint, **pass both** `uploadKey` and `uploadedFiles` (file name list)
    4. Backend automatically syncs files to plan directory during execution, making them accessible to agents
- **Response Example**:
  ```json
  {
    "planId": "plan-123456",
    "status": "processing",
    "message": "Task submitted, processing",
    "conversationId": "conv-123",
    "toolName": "my-tool",
    "planTemplateId": "template-456"
  }
  ```
- **Function**: Executes plan tasks asynchronously, immediately returns `planId`, task runs in background. Suitable for long-running tasks.

### Synchronous Execution

- **API File**: `LynxeController.java`
- **Main Endpoint**: `POST /api/executor/executeByToolNameSync`
- **Request Parameters**: Same as asynchronous execution
- **Response**: Returns execution result directly (waits for task completion)
- **Function**: Executes plan tasks synchronously, waits for completion before returning result. Suitable for quick tasks.

## 4. Task Status Tracking

### Get Execution Details (Core Interface)

- **API File**: `LynxeController.java`
- **Main Endpoint**: `GET /api/executor/details/{planId}`
- **Function**: Get complete execution record and status for specified planId
- **Response Structure**: `PlanExecutionRecord` object, containing:
  - `currentPlanId`: Current plan ID
  - `rootPlanId`: Root plan ID (for sub-plans)
  - `title`: Plan title
  - `status`: Execution status (pending/running/completed/failed)
  - `completed`: Whether completed
  - `summary`: Execution summary
  - `agentExecutionSequence`: Agent execution sequence (core data)
    - Each element contains: `agentName`, `agentRequest`, `result`, `status`, `stepId`, etc.
  - `userInputWaitState`: User input wait state (e.g., when task requires user form input)
  - `structureResult`: Structured result (extracted last tool call result when task completes)
  - `subPlanExecutionRecords`: Sub-plan execution records (supports nesting)

- **Use Cases**:
  - Frontend polls this endpoint to track task progress
  - Get complete execution history
  - Check if user input is required

### Get Step Details

- **API File**: `LynxeController.java`
- **Main Endpoint**: `GET /api/executor/agent-execution/{stepId}`
- **Function**: Get detailed step information for specified stepId
- **Response Structure**: `AgentExecutionRecord` object, containing:
  - `agentName`: Agent name
  - `agentRequest`: Agent request content
  - `agentDescription`: Agent description
  - `result`: Execution result
  - `errorMessage`: Error message
  - `status`: Status (RUNNING/FINISHED/IDLE)
  - `thinkActRecords`: Think-Act record list
    - Each record contains: `thinkInput`, `thinkOutput`, `inputCharCount`, `outputCharCount`, `actToolInfoList`, etc.

- **Use Cases**:
  - Display detailed step execution information in right panel
  - View thinking process and tool calls for each step

### Auxiliary Endpoints

- **Delete Execution Record**: `DELETE /api/executor/details/{planId}`
  - Function: Delete execution record for specified planId (does not actually delete from database, only marks)

- **Submit User Input**: `POST /api/executor/submit-input/{planId}`
  - Function: When task is waiting for user input (e.g., form filling), submit user input data
  - Request Body: `{ "field1": "value1", "field2": "value2" }`

## 5. Frontend Implementation Reference

### Execution Flow

Refer to `ui-vue3/src/components/sidebar/ExecutionController.vue`:
- Call `executeByToolNameAsync` to initiate task
- Get returned `planId`
- Use `planId` for status tracking

### Status Tracking

Refer to `ui-vue3/src/composables/usePlanExecution.ts`:
- Use `CommonApiService.getDetails(planId)` to poll execution status
- Default polling interval: 1 second
- Automatically handles retries and error recovery

### Details Display

Refer to `ui-vue3/src/components/chat/ExecutionDetails.vue`:
- Displays complete structure of `PlanExecutionRecord`
- Supports nested sub-plan display
- Supports step selection and detail viewing

Refer to `ui-vue3/src/composables/useRightPanel.ts`:
- Handles step selection events
- Calls `fetchAgentExecutionDetail(stepId)` to get step details
- Displays detailed execution information in right panel

## 6. Configuration Module

- **API File**: `ConfigController.java`
- **Main Endpoints**:
  - `GET /api/config/group/{groupName}`: Get configuration items by group
  - `POST /api/config/batch-update`: Batch update configuration items

- **Configuration Entity**: `ConfigEntity`
- **Service**: `ConfigService`

## 7. Core Interfaces for Plan-Act Pattern

- **API File**: `PlanTemplateController.java`
- **Main Endpoints**:
  - `POST /api/plan-template/generate`: Generate a new plan template
  - `POST /api/plan-template/executePlanByTemplateId`: Execute plan by template ID
  - `POST /api/plan-template/save`: Save plan version
  - `POST /api/plan-template/versions`: Get plan version history
  - `POST /api/plan-template/get-version`: Get a specific version of a plan
  - `GET /api/plan-template/list`: Get all plan templates
  - `POST /api/plan-template/update`: Update plan template
  - `POST /api/plan-template/delete`: Delete plan template

- Plan generation, version management, and execution are coordinated by `PlanTemplateService`, `PlanIdDispatcher`, and `PlanningFactory`.

## 8. Data Model Description

### PlanExecutionRecord (Execution Record)

This is the core data structure of the system. All execution feedback is returned through this object to simplify integration. Main fields:

- **Identification**: `currentPlanId`, `rootPlanId`, `parentPlanId`
- **Execution Status**: `status`, `completed`, `currentStepIndex`
- **Execution Content**: `agentExecutionSequence` (agent execution sequence)
- **User Interaction**: `userInputWaitState` (user input wait state)
- **Result Data**: `summary`, `structureResult` (structured result)

For detailed field descriptions, please refer to the source code comments in `src/main/java/com/alibaba/cloud/ai/lynxe/recorder/entity/vo/PlanExecutionRecord.java`.

### AgentExecutionRecord (Agent Execution Record)

Execution record for a single agent, containing:

- **Basic Information**: `agentName`, `agentRequest`, `agentDescription`
- **Execution Result**: `result`, `errorMessage`
- **Status Information**: `status`, `stepId`
- **Sub-plans**: `subPlanExecutionRecords` (supports nested sub-plans)
- **Think-Act Records**: `thinkActRecords` (obtained via `/api/executor/agent-execution/{stepId}`)

## 9. Quick Debugging Tips

1. **After starting the service, use Postman or curl to test endpoints**:
   ```bash
   # 1. Upload files (optional)
   curl -X POST http://localhost:8080/api/file-upload/upload \
     -F "files=@/path/to/file1.pdf" \
     -F "files=@/path/to/file2.txt"
   # Returns: {"uploadKey": "upload-xxx", "uploadedFiles": [{"originalName": "file1.pdf", ...}, ...]}
   
   # 2. Asynchronous execution (with uploaded files)
   curl -X POST http://localhost:8080/api/executor/executeByToolNameAsync \
     -H "Content-Type: application/json" \
     -d '{
       "toolName": "my-tool",
       "serviceGroup": "research",
       "replacementParams": {"param1": "value1"},
       "uploadedFiles": ["file1.pdf", "file2.txt"],
       "uploadKey": "upload-xxx"
     }'
   
   # 3. Get execution details (use returned planId)
   curl http://localhost:8080/api/executor/details/{planId}
   
   # 4. Get step details
   curl http://localhost:8080/api/executor/agent-execution/{stepId}
   ```

2. **Track asynchronous task progress via `/api/executor/details/{planId}`**:
   - Frontend polls every 1 second by default
   - Can be called manually to check current status
   - Returns complete execution tree structure (including sub-plans)

3. **Use `/api/config/batch-update` to modify configuration without restarting the service**

4. **All plan template operations can be performed via `/api/plan-template/*` endpoints**

5. **View frontend implementation**:
   - `ExecutionController.vue`: Task initiation example
   - `ExecutionDetails.vue`: Execution details display
   - `usePlanExecution.ts`: Status polling implementation
   - `useRightPanel.ts`: Step detail retrieval

---

For more detailed interface parameters, return structures, or business logic, please refer to the corresponding Controller source code and Service layer implementations.
