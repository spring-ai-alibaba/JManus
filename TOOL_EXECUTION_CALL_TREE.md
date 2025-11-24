# Tool Execution Call Tree

## Overview
This diagram shows the call flow for how tools are executed when a plan runs, including how `selectedToolKeys` are used to filter and execute tools.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         FRONTEND LAYER                                   │
└─────────────────────────────────────────────────────────────────────────┘

1. User Triggers Execution
   └─> ExecutionController.vue
       │
       ├─> [User clicks "Execute Plan" button]
       │   └─> handleExecutePlan()
       │       │
       │       └─> proceedWithExecution()
       │           │
       │           └─> messageDialog.executePlan(finalPayload)
       │               │
       │               └─> useMessageDialog.ts::executePlan()
       │                   │
       │                   └─> PlanActApiService.executePlan()
       │                       │
       │                       └─> DirectApiService.executeByToolName()
       │                           │
       │                           └─> HTTP POST /api/executor/executeByToolNameAsync
       │                               │
       │                               └─> Request Body:
       │                                   {
       │                                     "toolName": planTemplateId,
       │                                     "replacementParams": {...},
       │                                     "uploadedFiles": [...],
       │                                     "conversationId": "...",
       │                                     "requestSource": "VUE_SIDEBAR"
       │                                   }

2. Plan Data Preparation
   └─> ExecutionController.vue::proceedWithExecution()
       │
       └─> Converts templateConfig to PlanData:
           {
             "title": "...",
             "steps": [
               {
                 "stepRequirement": "...",
                 "agentName": "...",
                 "modelName": "...",
                 "selectedToolKeys": ["tool1", "tool2", ...],  // From step config
                 "terminateColumns": "..."
               }
             ],
             "planTemplateId": "..."
           }


┌─────────────────────────────────────────────────────────────────────────┐
│                         BACKEND LAYER                                    │
└─────────────────────────────────────────────────────────────────────────┘

3. Execution Controller Entry Point
   └─> LynxeController.java
       │
       └─> @PostMapping("/executeByToolNameAsync")
           │
           └─> executeByToolNameAsync(request)
               │
               ├─> getPlanTemplateIdFromTool(toolName, serviceGroup)
               │   └─> [Resolves planTemplateId from tool name]
               │
               └─> executePlanTemplate(planTemplateId, uploadedFiles, conversationId, replacementParams, uploadKey)
                   │
                   └─> Creates ExecutionContext with:
                       - planTemplateId
                       - uploadedFiles
                       - conversationId
                       - replacementParams
                       - uploadKey

4. Plan Loading & Execution
   └─> LynxeController::executePlanTemplate()
       │
       ├─> planTemplateService.getLatestPlanVersion(planTemplateId)
       │   └─> [Loads plan JSON from database]
       │
       ├─> objectMapper.readValue(planJson, PlanInterface.class)
       │   └─> [Parses JSON to PlanInterface]
       │       └─> PlanInterface.getAllSteps()
       │           └─> Returns List<ExecutionStep>
       │               └─> Each ExecutionStep contains:
       │                   - stepRequirement
       │                   - agentName
       │                   - modelName
       │                   - selectedToolKeys: List<String>  // Key field!
       │                   - terminateColumns
       │
       └─> planExecutor.executePlan(planInterface, executionContext)
           │
           └─> AbstractPlanExecutor.executePlan()
               │
               └─> For each step in plan:
                   │
                   └─> executeStep(step, context)
                       │
                       └─> getExecutorForStep(context, step)
                           │
                           └─> DynamicToolPlanExecutor.getExecutorForStep()
                               │
                               ├─> step.getSelectedToolKeys()  // Extract selectedToolKeys
                               │
                               └─> createConfigurableDynaAgent(
                                       planId, rootPlanId, initSettings,
                                       expectedReturnInfo, step,
                                       modelName,
                                       selectedToolKeys,  // Pass to agent
                                       planDepth, conversationId
                                   )

5. Agent Creation with Selected Tools
   └─> DynamicToolPlanExecutor::createConfigurableDynaAgent()
       │
       └─> new ConfigurableDynaAgent(
               llmService, recorder, properties,
               name, description, nextStepPrompt,
               selectedToolKeys,  // Available tools for this agent
               toolCallingManager, initSettings,
               userInputService, modelName,
               streamingResponseHandler, step,
               planIdDispatcher, ...
           )
           │
           └─> agent.setToolCallbackProvider(...)
               │
               └─> planningFactory.toolCallbackMap(planId, rootPlanId, expectedReturnInfo)
                   │
                   └─> [Creates map of all available tools]

6. Agent Execution
   └─> AbstractPlanExecutor::executeStep()
       │
       └─> executor.run()  // BaseAgent.run()
           │
           └─> ConfigurableDynaAgent.run() (inherited from DynamicAgent)
               │
               └─> DynamicAgent.run()
                   │
                   ├─> think()  // LLM generates tool calls
                   │   │
                   │   └─> getToolCallList()  // Get available tools
                   │       │
                   │       └─> ConfigurableDynaAgent.getToolCallList()
                   │           │
                   │           ├─> toolCallbackProvider.getToolCallBackContext()
                   │           │   └─> [Gets all available tools]
                   │           │
                   │           └─> Filter tools by availableToolKeys (selectedToolKeys)
                   │               │
                   │               └─> For each toolKey in availableToolKeys:
                   │                   │
                   │                   ├─> Find tool in toolCallBackContext
                   │                   │   (supports qualified keys: "serviceGroup.toolName")
                   │                   │
                   │                   └─> Add toolCallback to list
                   │
                   └─> act()  // Execute selected tools
                       │
                       └─> DynamicAgent.act()
                           │
                           ├─> streamResult.getEffectiveToolCalls()
                           │   └─> [LLM selected tools from filtered list]
                           │
                           ├─> If single tool:
                           │   └─> processSingleTool(toolCall)
                           │       │
                           │       └─> toolCallingManager.executeToolCalls(userPrompt, response)
                           │           │
                           │           └─> Execute tool.apply(input, toolContext)
                           │
                           └─> If multiple tools:
                               └─> processMultipleTools(toolCalls)
                                   │
                                   └─> parallelToolExecutionService.executeToolsInParallel(...)
                                       │
                                       └─> For each toolCall:
                                           │
                                           └─> CompletableFuture.supplyAsync(() -> {
                                                   functionInstance.apply(input, toolContext)
                                               })
                                               │
                                               └─> [Parallel execution]

7. Tool Execution
   └─> Tool.apply(input, toolContext)
       │
       ├─> AbstractBaseTool.apply()
       │   └─> [Tool-specific implementation]
       │
       └─> ToolExecuteResult returned
           │
           └─> Result processed and added to conversation history


┌─────────────────────────────────────────────────────────────────────────┐
│                         DATA FLOW SUMMARY                                │
└─────────────────────────────────────────────────────────────────────────┘

Frontend (ExecutionController.vue)
    │
    ├─> templateConfig.getConfig()
    │   └─> config.steps[].selectedToolKeys: string[]
    │
    └─> PlanData.steps[].selectedToolKeys: string[]
        │
        ▼

HTTP Request
    │
    └─> POST /api/executor/executeByToolNameAsync
        │
        └─> Request Body (planTemplateId used to load plan)
            │
            ▼

Backend (LynxeController)
    │
    ├─> Load plan JSON from database
    │   └─> Plan JSON contains ExecutionStep[]
    │       └─> ExecutionStep.selectedToolKeys: List<String>
    │
    └─> Parse to PlanInterface
        │
        ▼

Plan Executor (DynamicToolPlanExecutor)
    │
    ├─> For each ExecutionStep:
    │   │
    │   └─> step.getSelectedToolKeys()
    │       │
    │       └─> Pass to createConfigurableDynaAgent()
    │           │
    │           └─> ConfigurableDynaAgent(..., selectedToolKeys, ...)
    │               │
    │               └─> Agent stores selectedToolKeys as availableToolKeys
    │                   │
    │                   ▼

Agent Execution (ConfigurableDynaAgent)
    │
    ├─> getToolCallList()
    │   │
    │   └─> Filter tools by availableToolKeys (selectedToolKeys)
    │       │
    │       └─> Only tools in selectedToolKeys are available to LLM
    │           │
    │           ▼

LLM Tool Selection
    │
    ├─> LLM receives filtered tool list
    │   └─> LLM selects tools from filtered list
    │       │
    │       └─> ToolCall[] generated
    │           │
    │           ▼

Tool Execution
    │
    ├─> processSingleTool() or processMultipleTools()
    │   │
    │   └─> Execute selected tools
    │       │
    │       └─> Tool.apply(input, toolContext)
    │           │
    │           └─> ToolExecuteResult returned
    │               │
    │               └─> Result added to conversation history


┌─────────────────────────────────────────────────────────────────────────┐
│                         KEY FILES & METHODS                              │
└─────────────────────────────────────────────────────────────────────────┘

Frontend:
  • ui-vue3/src/components/sidebar/ExecutionController.vue
    - Line 526-577: proceedWithExecution() - prepares plan data with selectedToolKeys
    - Line 566: selectedToolKeys: [] (initialized, should use step.selectedToolKeys)

  • ui-vue3/src/api/plan-act-api-service.ts
    - Line 26-62: executePlan() - calls DirectApiService.executeByToolName()

  • ui-vue3/src/api/direct-api-service.ts
    - Line 67-147: executeByToolName() - sends HTTP request to backend

Backend:
  • src/main/java/.../runtime/controller/LynxeController.java
    - Line 197-303: executeByToolNameAsync() - entry point for execution
    - Line 554-577: executePlanTemplate() - loads plan and starts execution

  • src/main/java/.../runtime/executor/DynamicToolPlanExecutor.java
    - Line 122-148: getExecutorForStep() - extracts selectedToolKeys from step
    - Line 138: List<String> selectedToolKeys = step.getSelectedToolKeys()
    - Line 150-180: createConfigurableDynaAgent() - creates agent with selectedToolKeys

  • src/main/java/.../agent/ConfigurableDynaAgent.java
    - Line 75-89: Constructor - receives selectedToolKeys as availableToolKeys
    - Line 98-185: getToolCallList() - filters tools by availableToolKeys
    - Line 103-107: If availableToolKeys is empty, uses all tools
    - Line 160-181: Builds tool callback list from filtered availableToolKeys

  • src/main/java/.../agent/DynamicAgent.java
    - Line 594-635: act() - executes tools selected by LLM
    - Line 642-686: processSingleTool() - executes single tool
    - Line 802-856: processMultipleTools() - executes multiple tools in parallel

  • src/main/java/.../runtime/service/ParallelToolExecutionService.java
    - Line 112-165: executeToolsInParallel() - parallel tool execution

  • src/main/java/.../runtime/entity/vo/ExecutionStep.java
    - Line 53: private List<String> selectedToolKeys
    - Line 140-145: getter/setter methods


┌─────────────────────────────────────────────────────────────────────────┐
│                         EXECUTION FLOW DIAGRAM                           │
└─────────────────────────────────────────────────────────────────────────┘

User clicks "Execute Plan"
    │
    ▼
ExecutionController.vue
    │
    ├─> Get plan config with selectedToolKeys
    │
    └─> Call PlanActApiService.executePlan()
        │
        ▼
HTTP POST /api/executor/executeByToolNameAsync
    │
    ▼
LynxeController.executeByToolNameAsync()
    │
    ├─> Resolve planTemplateId
    │
    └─> executePlanTemplate()
        │
        ├─> Load plan JSON from database
        │   └─> Contains ExecutionStep[].selectedToolKeys
        │
        └─> planExecutor.executePlan()
            │
            ▼
AbstractPlanExecutor.executePlan()
    │
    └─> For each ExecutionStep:
        │
        └─> executeStep(step, context)
            │
            └─> getExecutorForStep(context, step)
                │
                ▼
DynamicToolPlanExecutor.getExecutorForStep()
    │
    ├─> Extract: step.getSelectedToolKeys()
    │
    └─> createConfigurableDynaAgent(..., selectedToolKeys, ...)
        │
        ▼
ConfigurableDynaAgent Constructor
    │
    └─> Stores selectedToolKeys as availableToolKeys
        │
        ▼
Agent.run()
    │
    ├─> think()
    │   │
    │   └─> getToolCallList()
    │       │
    │       └─> Filter tools by availableToolKeys
    │           │
    │           └─> LLM receives filtered tool list
    │
    └─> act()
        │
        ├─> LLM selects tools from filtered list
        │
        └─> Execute selected tools
            │
            ├─> Single tool: processSingleTool()
            │
            └─> Multiple tools: processMultipleTools()
                │
                └─> Parallel execution via CompletableFuture


┌─────────────────────────────────────────────────────────────────────────┐
│                         IMPORTANT NOTES                                  │
└─────────────────────────────────────────────────────────────────────────┘

1. selectedToolKeys Filtering:
   - selectedToolKeys from ExecutionStep are passed to ConfigurableDynaAgent
   - Agent filters available tools using getToolCallList()
   - Only tools in selectedToolKeys are presented to LLM
   - LLM can only select from this filtered list

2. Tool Key Format:
   - Tools use qualified keys: "serviceGroup.toolName"
   - ConfigurableDynaAgent supports both qualified and unqualified keys
   - Backward compatibility lookup is performed if exact match fails

3. Empty selectedToolKeys:
   - If selectedToolKeys is null or empty, all available tools are used
   - This provides backward compatibility for plans without tool selection

4. Parallel Execution:
   - Multiple tools can be executed in parallel
   - Uses CompletableFuture for async execution
   - Results are collected and processed after all tools complete

5. Tool Execution Context:
   - Each tool receives ToolContext with toolCallId and planDepth
   - Context is used for tracking and linking tool calls in plan hierarchy

